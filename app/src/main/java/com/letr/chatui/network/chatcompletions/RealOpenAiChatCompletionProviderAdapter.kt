package com.letr.chatui.network.chatcompletions

import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.Buffer
import okio.BufferedSource
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference

fun interface OpenAiChatCompletionProviderAdapterFactory {
    fun create(config: OpenAiProviderConfig): OpenAiChatCompletionProviderAdapter
}

class RealOpenAiChatCompletionProviderAdapterFactory(
    private val okHttpClient: OkHttpClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : OpenAiChatCompletionProviderAdapterFactory {
    override fun create(config: OpenAiProviderConfig): OpenAiChatCompletionProviderAdapter {
        return RealOpenAiChatCompletionProviderAdapter(
            okHttpClient = okHttpClient,
            providerConfig = config,
            ioDispatcher = ioDispatcher,
        )
    }
}

class RealOpenAiChatCompletionProviderAdapter(
    private val okHttpClient: OkHttpClient,
    private val providerConfig: OpenAiProviderConfig,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : OpenAiChatCompletionProviderAdapter {

    override suspend fun createChatCompletion(
        request: OpenAiChatCompletionRequestDto,
    ): OpenAiChatCompletionResponseDto {
        okHttpClient.newCall(buildRequest(request)).execute().use { response ->
            response.throwIfUnsuccessful()
            val body = response.body?.string()
                ?: throw OpenAiChatCompletionProtocolException("Chat completion response body was empty.")
            return parseCompletionResponse(body)
        }
    }

    override fun streamChatCompletion(
        request: OpenAiChatCompletionRequestDto,
    ): OpenAiChatCompletionStreamingSession {
        return RealOpenAiChatCompletionStreamingSession(
            okHttpClient = okHttpClient,
            httpRequest = buildRequest(request),
            ioDispatcher = ioDispatcher,
        )
    }

    private fun buildRequest(request: OpenAiChatCompletionRequestDto): Request {
        return Request.Builder()
            .url(providerConfig.chatCompletionsUrl())
            .header("Authorization", "Bearer ${providerConfig.apiKey.orEmpty()}")
            .header("Content-Type", "application/json")
            .header("Accept", if (request.stream) "text/event-stream" else "application/json")
            .post(request.toJson().toRequestBody(JSON_MEDIA_TYPE))
            .build()
    }
}

private class RealOpenAiChatCompletionStreamingSession(
    private val okHttpClient: OkHttpClient,
    private val httpRequest: Request,
    private val ioDispatcher: CoroutineDispatcher,
) : OpenAiChatCompletionStreamingSession {
    private val activeCall = AtomicReference<Call?>(null)

    override val events: Flow<OpenAiChatCompletionStreamEvent> = callbackFlow {
        val parser = OpenAiChatCompletionStreamParser()
        val call = okHttpClient.newCall(httpRequest)
        activeCall.set(call)

        val readerJob = launch(ioDispatcher) {
            try {
                call.execute().use { response ->
                    response.throwIfUnsuccessful()
                    val body = response.body
                        ?: throw OpenAiChatCompletionProtocolException("Streaming response body was empty.")
                    readSseStream(body.source(), parser) { event ->
                        trySend(event).getOrThrow()
                    }
                }
                close()
            } catch (throwable: Throwable) {
                val failure = if (call.isCanceled()) {
                    CancellationException("Streaming call cancelled.", throwable)
                } else {
                    throwable
                }
                close(failure)
            } finally {
                activeCall.compareAndSet(call, null)
            }
        }

        awaitClose {
            call.cancel()
            readerJob.cancel()
            activeCall.compareAndSet(call, null)
        }
    }

    override fun cancel() {
        activeCall.get()?.cancel()
    }
}

private fun readSseStream(
    source: BufferedSource,
    parser: OpenAiChatCompletionStreamParser,
    onEvent: (OpenAiChatCompletionStreamEvent) -> Unit,
) {
    while (true) {
        val line = source.readUtf8Line() ?: break
        if (line.isBlank() || line.startsWith(":")) {
            continue
        }
        if (!line.startsWith("data:")) {
            throw OpenAiChatCompletionProtocolException("Malformed SSE frame: $line")
        }

        parser.parse(line).forEach(onEvent)
        if (parser.snapshot().isTerminal) {
            return
        }
    }

    if (!parser.snapshot().isTerminal) {
        throw OpenAiChatCompletionInterruptedStreamException()
    }
}

private fun Response.throwIfUnsuccessful() {
    if (!isSuccessful) {
        throw OpenAiChatCompletionHttpException(
            statusCode = code,
            retryAfterSeconds = header("Retry-After")?.trim()?.toLongOrNull(),
        )
    }
}

private fun OpenAiProviderConfig.chatCompletionsUrl(): String {
    val normalizedBaseUrl = baseUrl.trim().removeSuffix("/")
    val resolved = if (normalizedBaseUrl.endsWith("/v1")) {
        "$normalizedBaseUrl/chat/completions"
    } else {
        "$normalizedBaseUrl/v1/chat/completions"
    }
    return checkNotNull(resolved.toHttpUrlOrNull()) {
        "Invalid chat completions URL: $resolved"
    }.toString()
}

private fun OpenAiChatCompletionRequestDto.toJson(): String {
    val buffer = Buffer()
    val writer = JsonWriter.of(buffer)

    writer.beginObject()
    writer.name("model").value(model)
    writer.name("messages")
    writer.beginArray()
    messages.forEach { message ->
        writer.beginObject()
        writer.name("role").value(message.role)
        writer.name("content")
        writer.beginArray()
        message.content.forEach { part ->
            writer.beginObject()
            when (part) {
                is OpenAiChatMessageContentPartDto.Text -> {
                    writer.name("type").value("text")
                    writer.name("text").value(part.text)
                }

                is OpenAiChatMessageContentPartDto.ImageUrl -> {
                    writer.name("type").value("image_url")
                    writer.name("image_url")
                    writer.beginObject()
                    writer.name("url").value(part.url)
                    writer.endObject()
                }
            }
            writer.endObject()
        }
        writer.endArray()
        writer.endObject()
    }
    writer.endArray()
    writer.name("stream").value(stream)
    writer.endObject()
    writer.close()
    return buffer.readUtf8()
}

private fun parseCompletionResponse(payload: String): OpenAiChatCompletionResponseDto {
    val reader = JsonReader.of(Buffer().writeUtf8(payload))
    try {
        var id: String? = null
        var created: Long? = null
        var model: String? = null
        val choices = mutableListOf<OpenAiChatCompletionChoiceDto>()

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "id" -> id = reader.nextNullableString()
                "created" -> created = reader.nextNullableLong()
                "model" -> model = reader.nextNullableString()
                "choices" -> {
                    reader.beginArray()
                    while (reader.hasNext()) {
                        choices += reader.readResponseChoice()
                    }
                    reader.endArray()
                }

                else -> reader.skipValue()
            }
        }
        reader.endObject()

        return OpenAiChatCompletionResponseDto(
            id = id,
            created = created,
            model = model,
            choices = choices,
        )
    } catch (exception: Exception) {
        throw OpenAiChatCompletionProtocolException("Malformed chat completion response.", exception)
    } finally {
        reader.close()
    }
}

private fun JsonReader.readResponseChoice(): OpenAiChatCompletionChoiceDto {
    var index = 0
    var finishReason: String? = null
    var message: OpenAiAssistantMessageDto? = null

    beginObject()
    while (hasNext()) {
        when (nextName()) {
            "index" -> index = nextInt()
            "finish_reason" -> finishReason = nextNullableString()
            "message" -> message = readAssistantMessage()
            else -> skipValue()
        }
    }
    endObject()

    return OpenAiChatCompletionChoiceDto(
        index = index,
        message = message,
        finishReason = finishReason,
    )
}

private fun JsonReader.readAssistantMessage(): OpenAiAssistantMessageDto {
    var role: String? = null
    var content: String? = null

    beginObject()
    while (hasNext()) {
        when (nextName()) {
            "role" -> role = nextNullableString()
            "content" -> content = nextNullableString()
            else -> skipValue()
        }
    }
    endObject()

    return OpenAiAssistantMessageDto(role = role, content = content)
}

private fun JsonReader.nextNullableString(): String? {
    return if (peek() == JsonReader.Token.NULL) {
        nextNull<String>()
    } else {
        nextString()
    }
}

private fun JsonReader.nextNullableLong(): Long? {
    return if (peek() == JsonReader.Token.NULL) {
        nextNull<Long>()
    } else {
        nextLong()
    }
}

private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

class OpenAiChatCompletionHttpException(
    val statusCode: Int,
    val retryAfterSeconds: Long?,
) : IOException("HTTP $statusCode")

open class OpenAiChatCompletionProtocolException(
    detail: String,
    cause: Throwable? = null,
) : IOException(detail, cause)

class OpenAiChatCompletionInterruptedStreamException :
    OpenAiChatCompletionProtocolException("Stream interrupted before terminal event.")

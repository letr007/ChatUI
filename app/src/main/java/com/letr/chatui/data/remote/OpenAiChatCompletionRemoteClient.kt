package com.letr.chatui.data.remote

import android.content.ContentResolver
import android.net.Uri
import android.util.Base64
import com.letr.chatui.data.model.ActiveChatRuntimeConfig
import com.letr.chatui.data.model.Message
import com.letr.chatui.data.model.MessageAuthor
import com.letr.chatui.data.repository.ActiveChatConfigSource
import com.letr.chatui.network.chatcompletions.OpenAiChatCompletionErrorMapper
import com.letr.chatui.network.chatcompletions.OpenAiChatCompletionFailure
import com.letr.chatui.network.chatcompletions.OpenAiChatCompletionHttpException
import com.letr.chatui.network.chatcompletions.OpenAiChatCompletionProviderAdapterFactory
import com.letr.chatui.network.chatcompletions.OpenAiChatCompletionRequestDto
import com.letr.chatui.network.chatcompletions.OpenAiChatCompletionResponseDto
import com.letr.chatui.network.chatcompletions.OpenAiChatCompletionStreamEvent
import com.letr.chatui.network.chatcompletions.OpenAiChatCompletionStreamingSession
import com.letr.chatui.network.chatcompletions.OpenAiChatMessageDto
import com.letr.chatui.network.chatcompletions.OpenAiChatMessageContentPartDto
import com.letr.chatui.network.chatcompletions.OpenAiProviderConfig
import com.letr.chatui.network.chatcompletions.OpenAiProviderConfigValidator
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

sealed interface OpenAiRemoteResult<out T> {
    data class Success<T>(val value: T) : OpenAiRemoteResult<T>
    data class Failure(val failure: OpenAiChatCompletionFailure) : OpenAiRemoteResult<Nothing>
}

sealed interface OpenAiChatCompletionRemoteEvent {
    data object AssistantMessageStarted : OpenAiChatCompletionRemoteEvent

    data class AssistantMessageDelta(
        val deltaText: String,
        val accumulatedText: String,
    ) : OpenAiChatCompletionRemoteEvent

    data class AssistantMessageCompleted(
        val accumulatedText: String,
        val finishReason: String?,
    ) : OpenAiChatCompletionRemoteEvent

    data class AssistantMessageFailed(
        val accumulatedText: String,
        val failure: OpenAiChatCompletionFailure,
    ) : OpenAiChatCompletionRemoteEvent

    data class AssistantMessageCancelled(
        val accumulatedText: String,
    ) : OpenAiChatCompletionRemoteEvent
}

interface OpenAiChatCompletionRemoteStreamingSession {
    val events: Flow<OpenAiChatCompletionRemoteEvent>
    fun cancel()
}

interface ChatCompletionRemoteClient {
    fun streamChatCompletion(messages: List<Message>): OpenAiChatCompletionRemoteStreamingSession
}

interface OpenAiChatCompletionRequestFactoryContract {
    fun create(
        messages: List<Message>,
        providerConfig: OpenAiProviderConfig,
        stream: Boolean,
    ): OpenAiChatCompletionRequestDto
}

open class OpenAiChatCompletionRequestFactory(
    private val contentResolver: ContentResolver,
) : OpenAiChatCompletionRequestFactoryContract {
    override open fun create(
        messages: List<Message>,
        providerConfig: OpenAiProviderConfig,
        stream: Boolean,
    ): OpenAiChatCompletionRequestDto {
        return OpenAiChatCompletionRequestDto(
            model = providerConfig.modelId,
            messages = messages
                .filter { it.content.isNotBlank() || it.attachedImageUris.isNotEmpty() }
                .map { message ->
                    OpenAiChatMessageDto(
                        role = message.author.toRemoteRole(),
                        content = buildContentParts(message),
                    )
                },
            stream = stream,
        )
    }

    private fun buildContentParts(message: Message): List<OpenAiChatMessageContentPartDto> {
        val parts = mutableListOf<OpenAiChatMessageContentPartDto>()
        if (message.content.isNotBlank()) {
            parts += OpenAiChatMessageContentPartDto.Text(text = message.content)
        }
        if (message.author == MessageAuthor.USER) {
            message.attachedImageUris.forEach { uriString ->
                toDataUrl(uriString)?.let { dataUrl ->
                    parts += OpenAiChatMessageContentPartDto.ImageUrl(url = dataUrl)
                }
            }
        }
        return parts
    }

    private fun toDataUrl(uriString: String): String? {
        return runCatching {
            val uri = Uri.parse(uriString)
            val mimeType = contentResolver.getType(uri) ?: guessMimeType(uriString) ?: "image/jpeg"
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val bytes = inputStream.readBytes()
                if (bytes.isEmpty()) return null
                val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                "data:$mimeType;base64,$base64"
            }
        }.getOrNull()
    }

    private fun guessMimeType(uriString: String): String? {
        return when {
            uriString.endsWith(".png", ignoreCase = true) -> "image/png"
            uriString.endsWith(".webp", ignoreCase = true) -> "image/webp"
            uriString.endsWith(".gif", ignoreCase = true) -> "image/gif"
            uriString.endsWith(".jpg", ignoreCase = true) || uriString.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
            else -> null
        }
    }
}

class OpenAiChatCompletionStreamReducer(
    private val interruptedFailure: OpenAiChatCompletionFailure = OpenAiChatCompletionFailure.Protocol(
        detail = "Stream interrupted before terminal event.",
    ),
) {
    fun reduce(
        events: Flow<OpenAiChatCompletionStreamEvent>,
        mapFailure: (Throwable) -> OpenAiChatCompletionFailure = OpenAiChatCompletionErrorMapper::fromThrowable,
    ): Flow<OpenAiChatCompletionRemoteEvent> = callbackFlow {
        var started = false
        var terminal = false
        var accumulatedText = ""

        val job = launch {
            try {
                events.collect { event ->
                    when (event) {
                        is OpenAiChatCompletionStreamEvent.TextDelta -> {
                            if (!started) {
                                trySend(OpenAiChatCompletionRemoteEvent.AssistantMessageStarted).getOrThrow()
                                started = true
                            }
                            accumulatedText = event.accumulatedText
                            trySend(
                                OpenAiChatCompletionRemoteEvent.AssistantMessageDelta(
                                    deltaText = event.text,
                                    accumulatedText = accumulatedText,
                                )
                            ).getOrThrow()
                        }

                        is OpenAiChatCompletionStreamEvent.Completed -> {
                            if (!started) {
                                trySend(OpenAiChatCompletionRemoteEvent.AssistantMessageStarted).getOrThrow()
                                started = true
                            }
                            accumulatedText = event.accumulatedText
                            terminal = true
                            trySend(
                                OpenAiChatCompletionRemoteEvent.AssistantMessageCompleted(
                                    accumulatedText = accumulatedText,
                                    finishReason = event.finishReason,
                                )
                            ).getOrThrow()
                        }
                    }
                }

                if (!terminal && started) {
                    trySend(
                        OpenAiChatCompletionRemoteEvent.AssistantMessageFailed(
                            accumulatedText = accumulatedText,
                            failure = interruptedFailure,
                        )
                    ).getOrThrow()
                }
                close()
            } catch (throwable: Throwable) {
                val failure = mapFailure(throwable)
                when (failure) {
                    OpenAiChatCompletionFailure.Cancelled -> {
                        if (!started) {
                            trySend(OpenAiChatCompletionRemoteEvent.AssistantMessageStarted).getOrThrow()
                            started = true
                        }
                        trySend(
                            OpenAiChatCompletionRemoteEvent.AssistantMessageCancelled(
                                accumulatedText = accumulatedText,
                            )
                        ).getOrThrow()
                    }

                    else -> {
                        if (!started) {
                            trySend(OpenAiChatCompletionRemoteEvent.AssistantMessageStarted).getOrThrow()
                            started = true
                        }
                        trySend(
                            OpenAiChatCompletionRemoteEvent.AssistantMessageFailed(
                                accumulatedText = accumulatedText,
                                failure = failure,
                            )
                        ).getOrThrow()
                    }
                }
                close()
            }
        }

        awaitClose { job.cancel() }
    }
}

class ConfigBackedOpenAiChatCompletionRemoteClient(
    private val activeChatConfigSource: ActiveChatConfigSource,
    private val adapterFactory: OpenAiChatCompletionProviderAdapterFactory,
    private val requestFactory: OpenAiChatCompletionRequestFactoryContract,
    private val streamReducer: OpenAiChatCompletionStreamReducer = OpenAiChatCompletionStreamReducer(),
) : ChatCompletionRemoteClient {
    suspend fun createChatCompletion(messages: List<Message>): OpenAiRemoteResult<OpenAiChatCompletionResponseDto> {
        val providerConfig = activeChatConfigSource.getActiveConfig().toProviderConfig()
        OpenAiProviderConfigValidator.validate(providerConfig)?.let { failure ->
            return OpenAiRemoteResult.Failure(failure)
        }

        return try {
            OpenAiRemoteResult.Success(
                adapterFactory.create(providerConfig).createChatCompletion(
                    requestFactory.create(
                        messages = messages,
                        providerConfig = providerConfig,
                        stream = false,
                    )
                )
            )
        } catch (throwable: Throwable) {
            OpenAiRemoteResult.Failure(throwable.toFailure())
        }
    }

    override fun streamChatCompletion(messages: List<Message>): OpenAiChatCompletionRemoteStreamingSession {
        return ConfigBackedOpenAiChatCompletionRemoteStreamingSession(
            activeChatConfigSource = activeChatConfigSource,
            adapterFactory = adapterFactory,
            requestFactory = requestFactory,
            streamReducer = streamReducer,
            messages = messages,
        )
    }
}

private class ConfigBackedOpenAiChatCompletionRemoteStreamingSession(
    private val activeChatConfigSource: ActiveChatConfigSource,
    private val adapterFactory: OpenAiChatCompletionProviderAdapterFactory,
    private val requestFactory: OpenAiChatCompletionRequestFactoryContract,
    private val streamReducer: OpenAiChatCompletionStreamReducer,
    private val messages: List<Message>,
) : OpenAiChatCompletionRemoteStreamingSession {
    private val underlyingSession = AtomicReference<OpenAiChatCompletionStreamingSession?>(null)
    private val cancelledByCaller = AtomicBoolean(false)

    override val events: Flow<OpenAiChatCompletionRemoteEvent> = callbackFlow {
        val job = launch {
            try {
                val providerConfig = activeChatConfigSource.getActiveConfig().toProviderConfig()
                val validationFailure = OpenAiProviderConfigValidator.validate(providerConfig)
                if (validationFailure != null) {
                    trySend(
                        OpenAiChatCompletionRemoteEvent.AssistantMessageFailed(
                            accumulatedText = "",
                            failure = validationFailure,
                        )
                    ).getOrThrow()
                    close()
                    return@launch
                }

                if (cancelledByCaller.get()) {
                    trySend(
                        OpenAiChatCompletionRemoteEvent.AssistantMessageCancelled(accumulatedText = "")
                    ).getOrThrow()
                    close()
                    return@launch
                }

                val request = requestFactory.create(
                    messages = messages,
                    providerConfig = providerConfig,
                    stream = true,
                )
                val session = adapterFactory.create(providerConfig).streamChatCompletion(request)
                underlyingSession.set(session)

                streamReducer.reduce(
                    events = session.events,
                    mapFailure = { throwable ->
                        if (cancelledByCaller.get()) {
                            OpenAiChatCompletionFailure.Cancelled
                        } else {
                            throwable.toFailure()
                        }
                    }
                ).collect { event ->
                    trySend(event).getOrThrow()
                }
            } catch (throwable: Throwable) {
                val terminalEvent = if (cancelledByCaller.get()) {
                    OpenAiChatCompletionRemoteEvent.AssistantMessageCancelled(accumulatedText = "")
                } else {
                    OpenAiChatCompletionRemoteEvent.AssistantMessageFailed(
                        accumulatedText = "",
                        failure = throwable.toFailure(),
                    )
                }
                trySend(terminalEvent)
            }
            close()
        }

        awaitClose {
            job.cancel()
            underlyingSession.getAndSet(null)?.cancel()
        }
    }

    override fun cancel() {
        cancelledByCaller.set(true)
        underlyingSession.get()?.cancel()
    }
}

private fun MessageAuthor.toRemoteRole(): String {
    return when (this) {
        MessageAuthor.USER -> "user"
        MessageAuthor.ASSISTANT -> "assistant"
    }
}

private fun ActiveChatRuntimeConfig.toProviderConfig(): OpenAiProviderConfig {
    return OpenAiProviderConfig(
        baseUrl = apiBaseUrl,
        apiKey = apiKey,
        modelId = modelId,
    )
}

private fun Throwable.toFailure(): OpenAiChatCompletionFailure {
    return when (this) {
        is OpenAiChatCompletionHttpException -> OpenAiChatCompletionErrorMapper.fromHttpStatus(
            statusCode = statusCode,
            retryAfterSeconds = retryAfterSeconds,
        )

        else -> OpenAiChatCompletionErrorMapper.fromThrowable(this)
    }
}

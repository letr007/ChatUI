package com.letr.chatui.data.remote

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
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
    private companion object {
        const val TAG = "ChatUI-Images"
        const val MAX_IMAGE_DIMENSION = 1280
        const val TARGET_MAX_BYTES = 800 * 1024
        const val MIN_JPEG_QUALITY = 55
    }

    override open fun create(
        messages: List<Message>,
        providerConfig: OpenAiProviderConfig,
        stream: Boolean,
    ): OpenAiChatCompletionRequestDto {
        val attachmentCount = messages.sumOf { it.attachedImageUris.size }
        logDebug(
            TAG,
            "create request stream=$stream messages=${messages.size} attachments=$attachmentCount model=${providerConfig.modelId}"
        )
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
            val compressedImage = compressImageForUpload(uri)
            val mimeType = compressedImage?.mimeType ?: contentResolver.getType(uri) ?: guessMimeType(uriString) ?: "image/jpeg"
            val bytes = compressedImage?.bytes ?: contentResolver.openInputStream(uri)?.use { inputStream -> inputStream.readBytes() }
            if (bytes == null || bytes.isEmpty()) return null
            logDebug(TAG, "encoded image uri=$uriString bytes=${bytes.size} mime=$mimeType")
            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            logDebug(TAG, "base64 image uri=$uriString chars=${base64.length}")
            "data:$mimeType;base64,$base64"
        }.onFailure { throwable ->
            logError(TAG, "failed to prepare image uri=$uriString", throwable)
        }.getOrNull()
    }

    private fun compressImageForUpload(uri: Uri): EncodedImage? {
        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        contentResolver.openInputStream(uri)?.use { inputStream ->
            BitmapFactory.decodeStream(inputStream, null, boundsOptions)
        } ?: return null

        if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) return null

        logDebug(TAG, "original image uri=$uri width=${boundsOptions.outWidth} height=${boundsOptions.outHeight}")

        val sampleSize = calculateInSampleSize(boundsOptions.outWidth, boundsOptions.outHeight)
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
        }
        val bitmap = contentResolver.openInputStream(uri)?.use { inputStream ->
            BitmapFactory.decodeStream(inputStream, null, decodeOptions)
        } ?: return null

        return bitmap.useScaledBitmap { scaledBitmap ->
            compressBitmapToJpeg(scaledBitmap)
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int): Int {
        var sampleSize = 1
        var currentWidth = width
        var currentHeight = height
        while (currentWidth > MAX_IMAGE_DIMENSION || currentHeight > MAX_IMAGE_DIMENSION) {
            sampleSize *= 2
            currentWidth /= 2
            currentHeight /= 2
        }
        return sampleSize.coerceAtLeast(1)
    }

    private fun compressBitmapToJpeg(bitmap: Bitmap): EncodedImage {
        var quality = 88
        var encodedBytes = encodeBitmap(bitmap, quality)
        while (encodedBytes.size > TARGET_MAX_BYTES && quality > MIN_JPEG_QUALITY) {
            quality -= 8
            encodedBytes = encodeBitmap(bitmap, quality)
        }
        logDebug(
            TAG,
            "compressed bitmap width=${bitmap.width} height=${bitmap.height} quality=$quality bytes=${encodedBytes.size}"
        )
        return EncodedImage(
            bytes = encodedBytes,
            mimeType = "image/jpeg",
        )
    }

    private fun encodeBitmap(bitmap: Bitmap, quality: Int): ByteArray {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        return outputStream.toByteArray()
    }

    private inline fun <T> Bitmap.useScaledBitmap(block: (Bitmap) -> T): T {
        val scaledBitmap = scaleDownIfNeeded(this)
        return try {
            block(scaledBitmap)
        } finally {
            if (scaledBitmap !== this && !scaledBitmap.isRecycled) {
                scaledBitmap.recycle()
            }
            if (!isRecycled) {
                recycle()
            }
        }
    }

    private fun scaleDownIfNeeded(bitmap: Bitmap): Bitmap {
        val longestSide = maxOf(bitmap.width, bitmap.height)
        if (longestSide <= MAX_IMAGE_DIMENSION) return bitmap
        val scale = MAX_IMAGE_DIMENSION.toFloat() / longestSide.toFloat()
        val targetWidth = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)
        logDebug(TAG, "scale image from ${bitmap.width}x${bitmap.height} to ${targetWidth}x${targetHeight}")
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    private data class EncodedImage(
        val bytes: ByteArray,
        val mimeType: String,
    )

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
                                if (!trySendSafely(OpenAiChatCompletionRemoteEvent.AssistantMessageStarted)) {
                                    return@collect
                                }
                                started = true
                            }
                            accumulatedText = event.accumulatedText
                            if (!trySendSafely(
                                OpenAiChatCompletionRemoteEvent.AssistantMessageDelta(
                                    deltaText = event.text,
                                    accumulatedText = accumulatedText,
                                )
                            )) {
                                return@collect
                            }
                        }

                        is OpenAiChatCompletionStreamEvent.Completed -> {
                            if (!started) {
                                if (!trySendSafely(OpenAiChatCompletionRemoteEvent.AssistantMessageStarted)) {
                                    return@collect
                                }
                                started = true
                            }
                            accumulatedText = event.accumulatedText
                            terminal = true
                            if (!trySendSafely(
                                OpenAiChatCompletionRemoteEvent.AssistantMessageCompleted(
                                    accumulatedText = accumulatedText,
                                    finishReason = event.finishReason,
                                )
                            )) {
                                return@collect
                            }
                        }
                    }
                }

                if (!terminal && started) {
                    trySendSafely(
                        OpenAiChatCompletionRemoteEvent.AssistantMessageFailed(
                            accumulatedText = accumulatedText,
                            failure = interruptedFailure,
                        )
                    )
                }
                close()
            } catch (throwable: Throwable) {
                val failure = mapFailure(throwable)
                when (failure) {
                    OpenAiChatCompletionFailure.Cancelled -> {
                        if (!started) {
                            if (!trySendSafely(OpenAiChatCompletionRemoteEvent.AssistantMessageStarted)) {
                                close()
                                return@launch
                            }
                            started = true
                        }
                        trySendSafely(
                            OpenAiChatCompletionRemoteEvent.AssistantMessageCancelled(
                                accumulatedText = accumulatedText,
                            )
                        )
                    }

                    else -> {
                        if (!started) {
                            if (!trySendSafely(OpenAiChatCompletionRemoteEvent.AssistantMessageStarted)) {
                                close()
                                return@launch
                            }
                            started = true
                        }
                        trySendSafely(
                            OpenAiChatCompletionRemoteEvent.AssistantMessageFailed(
                                accumulatedText = accumulatedText,
                                failure = failure,
                            )
                        )
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
    private companion object {
        const val TAG = "ChatUI-Stream"
    }

    private val underlyingSession = AtomicReference<OpenAiChatCompletionStreamingSession?>(null)
    private val cancelledByCaller = AtomicBoolean(false)

    override val events: Flow<OpenAiChatCompletionRemoteEvent> = callbackFlow {
        val job = launch {
            try {
                val providerConfig = activeChatConfigSource.getActiveConfig().toProviderConfig()
                val validationFailure = OpenAiProviderConfigValidator.validate(providerConfig)
                if (validationFailure != null) {
                    trySendSafely(
                        OpenAiChatCompletionRemoteEvent.AssistantMessageFailed(
                            accumulatedText = "",
                            failure = validationFailure,
                        )
                    )
                    close()
                    return@launch
                }

                if (cancelledByCaller.get()) {
                    trySendSafely(
                        OpenAiChatCompletionRemoteEvent.AssistantMessageCancelled(accumulatedText = "")
                    )
                    close()
                    return@launch
                }

                val request = requestFactory.create(
                    messages = messages,
                    providerConfig = providerConfig,
                    stream = true,
                )
                logDebug(TAG, "open stream messages=${request.messages.size} model=${request.model}")
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
                    if (!trySendSafely(event)) {
                        return@collect
                    }
                }
            } catch (throwable: Throwable) {
                logError(TAG, "stream session failed", throwable)
                val terminalEvent = if (cancelledByCaller.get()) {
                    OpenAiChatCompletionRemoteEvent.AssistantMessageCancelled(accumulatedText = "")
                } else {
                    OpenAiChatCompletionRemoteEvent.AssistantMessageFailed(
                        accumulatedText = "",
                        failure = throwable.toFailure(),
                    )
                }
                trySendSafely(terminalEvent)
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

private fun <T> ProducerScope<T>.trySendSafely(value: T): Boolean {
    return trySend(value).isSuccess
}

private fun logDebug(tag: String, message: String) {
    runCatching { android.util.Log.d(tag, message) }
}

private fun logError(tag: String, message: String, throwable: Throwable) {
    runCatching { android.util.Log.e(tag, message, throwable) }
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

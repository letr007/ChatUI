package com.letr.chatui.network.chatcompletions

import android.content.res.Resources
import com.letr.chatui.R
import kotlinx.coroutines.flow.Flow
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URISyntaxException
import java.net.UnknownHostException
import kotlin.coroutines.cancellation.CancellationException

data class OpenAiChatCompletionRequestDto(
    val model: String,
    val messages: List<OpenAiChatMessageDto>,
    val stream: Boolean,
)

data class OpenAiChatMessageDto(
    val role: String,
    val content: List<OpenAiChatMessageContentPartDto>,
)

sealed interface OpenAiChatMessageContentPartDto {
    data class Text(
        val text: String,
    ) : OpenAiChatMessageContentPartDto

    data class ImageUrl(
        val url: String,
    ) : OpenAiChatMessageContentPartDto
}

data class OpenAiChatCompletionResponseDto(
    val id: String?,
    val created: Long?,
    val model: String?,
    val choices: List<OpenAiChatCompletionChoiceDto>,
)

data class OpenAiChatCompletionChoiceDto(
    val index: Int,
    val message: OpenAiAssistantMessageDto?,
    val finishReason: String?,
)

data class OpenAiAssistantMessageDto(
    val role: String?,
    val content: String?,
)

data class OpenAiChatCompletionChunkDto(
    val id: String?,
    val created: Long?,
    val model: String?,
    val choices: List<OpenAiChatCompletionChunkChoiceDto>,
)

data class OpenAiChatCompletionChunkChoiceDto(
    val index: Int,
    val delta: OpenAiChatCompletionDeltaDto,
    val finishReason: String?,
)

data class OpenAiModelDto(
    val id: String,
)

data class OpenAiModelsResponseDto(
    val data: List<OpenAiModelDto>,
)

data class OpenAiChatCompletionDeltaDto(
    val role: String? = null,
    val content: String? = null,
)

sealed interface OpenAiChatCompletionStreamEvent {
    data class TextDelta(
        val choiceIndex: Int,
        val text: String,
        val accumulatedText: String,
    ) : OpenAiChatCompletionStreamEvent

    data class Completed(
        val accumulatedText: String,
        val finishReason: String?,
        val signal: OpenAiChatCompletionTerminalSignal,
    ) : OpenAiChatCompletionStreamEvent
}

enum class OpenAiChatCompletionTerminalSignal {
    FINISH_REASON,
    DONE_MARKER,
}

data class OpenAiChatCompletionStreamSnapshot(
    val accumulatedText: String,
    val isTerminal: Boolean,
    val finishReason: String?,
    val terminalSignal: OpenAiChatCompletionTerminalSignal?,
)

data class OpenAiProviderConfig(
    val baseUrl: String,
    val apiKey: String?,
    val modelId: String,
)

sealed interface OpenAiChatCompletionFailure {
    data class MissingConfiguration(val fieldName: String) : OpenAiChatCompletionFailure

    data class InvalidBaseUrl(val rawValue: String) : OpenAiChatCompletionFailure

    data object Timeout : OpenAiChatCompletionFailure

    data object NoNetwork : OpenAiChatCompletionFailure

    data object Unauthorized : OpenAiChatCompletionFailure

    data class RateLimited(val retryAfterSeconds: Long?) : OpenAiChatCompletionFailure

    data object Cancelled : OpenAiChatCompletionFailure

    data class Protocol(val detail: String) : OpenAiChatCompletionFailure

    data class UnexpectedHttpStatus(val statusCode: Int) : OpenAiChatCompletionFailure

    data class Unknown(val detail: String?) : OpenAiChatCompletionFailure
}

interface OpenAiChatCompletionProviderAdapter {
    suspend fun createChatCompletion(
        request: OpenAiChatCompletionRequestDto,
    ): OpenAiChatCompletionResponseDto

    suspend fun listModels(): OpenAiModelsResponseDto

    fun streamChatCompletion(
        request: OpenAiChatCompletionRequestDto,
    ): OpenAiChatCompletionStreamingSession
}

interface OpenAiChatCompletionStreamingSession {
    val events: Flow<OpenAiChatCompletionStreamEvent>

    fun cancel()
}

object OpenAiProviderConfigValidator {
    fun validate(config: OpenAiProviderConfig): OpenAiChatCompletionFailure? {
        return validateBaseUrlAndApiKey(config)
            ?: if (config.modelId.isBlank()) {
                OpenAiChatCompletionFailure.MissingConfiguration(fieldName = "modelId")
            } else {
                null
            }
    }

    fun validateForModelsList(config: OpenAiProviderConfig): OpenAiChatCompletionFailure? {
        return validateBaseUrlAndApiKey(config)
    }

    private fun validateBaseUrlAndApiKey(config: OpenAiProviderConfig): OpenAiChatCompletionFailure? {
        if (config.baseUrl.isBlank()) {
            return OpenAiChatCompletionFailure.MissingConfiguration(fieldName = "baseUrl")
        }
        if (config.apiKey.isNullOrBlank()) {
            return OpenAiChatCompletionFailure.MissingConfiguration(fieldName = "apiKey")
        }

        val normalizedBaseUrl = try {
            URI(config.baseUrl)
        } catch (_: IllegalArgumentException) {
            return OpenAiChatCompletionFailure.InvalidBaseUrl(rawValue = config.baseUrl)
        } catch (_: URISyntaxException) {
            return OpenAiChatCompletionFailure.InvalidBaseUrl(rawValue = config.baseUrl)
        }

        val scheme = normalizedBaseUrl.scheme?.lowercase()
        if (!normalizedBaseUrl.isAbsolute || scheme !in setOf("http", "https")) {
            return OpenAiChatCompletionFailure.InvalidBaseUrl(rawValue = config.baseUrl)
        }

        return null
    }
}

object OpenAiChatCompletionErrorMapper {
    fun fromHttpStatus(
        statusCode: Int,
        retryAfterSeconds: Long? = null,
    ): OpenAiChatCompletionFailure {
        return when (statusCode) {
            401 -> OpenAiChatCompletionFailure.Unauthorized
            429 -> OpenAiChatCompletionFailure.RateLimited(retryAfterSeconds = retryAfterSeconds)
            else -> OpenAiChatCompletionFailure.UnexpectedHttpStatus(statusCode = statusCode)
        }
    }

    fun fromThrowable(throwable: Throwable): OpenAiChatCompletionFailure {
        return when (throwable) {
            is CancellationException -> OpenAiChatCompletionFailure.Cancelled
            is SocketTimeoutException,
            is InterruptedIOException -> OpenAiChatCompletionFailure.Timeout
            is UnknownHostException,
            is ConnectException -> OpenAiChatCompletionFailure.NoNetwork
            else -> OpenAiChatCompletionFailure.Unknown(detail = throwable.message)
        }
    }
}

fun OpenAiChatCompletionFailure.toUiLabel(resources: Resources): String {
    return when (this) {
        is OpenAiChatCompletionFailure.MissingConfiguration -> resources.getString(R.string.openai_failure_label_settings_required)
        is OpenAiChatCompletionFailure.InvalidBaseUrl -> resources.getString(R.string.openai_failure_label_invalid_url)
        OpenAiChatCompletionFailure.Timeout -> resources.getString(R.string.openai_failure_label_timeout)
        OpenAiChatCompletionFailure.NoNetwork -> resources.getString(R.string.openai_failure_label_no_network)
        OpenAiChatCompletionFailure.Unauthorized -> resources.getString(R.string.openai_failure_label_unauthorized)
        is OpenAiChatCompletionFailure.RateLimited -> resources.getString(R.string.openai_failure_label_rate_limited)
        OpenAiChatCompletionFailure.Cancelled -> resources.getString(R.string.openai_failure_label_cancelled)
        is OpenAiChatCompletionFailure.Protocol -> resources.getString(R.string.openai_failure_label_protocol)
        is OpenAiChatCompletionFailure.UnexpectedHttpStatus -> resources.getString(R.string.openai_failure_label_http_status, statusCode)
        is OpenAiChatCompletionFailure.Unknown -> resources.getString(R.string.openai_failure_label_request_failed)
    }
}

fun OpenAiChatCompletionFailure.toUiMessage(resources: Resources): String {
    return when (this) {
        is OpenAiChatCompletionFailure.MissingConfiguration -> {
            resources.getString(
                R.string.openai_failure_message_missing_configuration,
                fieldName.toUserFacingFieldName(resources),
            )
        }

        is OpenAiChatCompletionFailure.InvalidBaseUrl -> {
            resources.getString(R.string.openai_failure_message_invalid_base_url, rawValue)
        }

        OpenAiChatCompletionFailure.Timeout -> resources.getString(R.string.openai_failure_message_timeout)
        OpenAiChatCompletionFailure.NoNetwork -> resources.getString(R.string.openai_failure_message_no_network)
        OpenAiChatCompletionFailure.Unauthorized -> {
            resources.getString(R.string.openai_failure_message_unauthorized)
        }

        is OpenAiChatCompletionFailure.RateLimited -> {
            retryAfterSeconds?.let {
                resources.getString(R.string.openai_failure_message_rate_limited_with_seconds, it.toInt())
            } ?: resources.getString(R.string.openai_failure_message_rate_limited_default)
        }

        OpenAiChatCompletionFailure.Cancelled -> resources.getString(R.string.openai_failure_message_cancelled)
        is OpenAiChatCompletionFailure.Protocol -> {
            resources.getString(R.string.openai_failure_message_protocol, detail)
        }

        is OpenAiChatCompletionFailure.UnexpectedHttpStatus -> {
            resources.getString(R.string.openai_failure_message_http_status, statusCode)
        }

        is OpenAiChatCompletionFailure.Unknown -> {
            val knownDetail = detail?.takeIf { it.isNotBlank() }
            if (knownDetail == null) {
                resources.getString(R.string.openai_failure_message_unknown)
            } else {
                resources.getString(R.string.openai_failure_message_unknown_with_detail, knownDetail)
            }
        }
    }
}

private fun String.toUserFacingFieldName(resources: Resources): String {
    return when (this) {
        "baseUrl" -> resources.getString(R.string.openai_field_base_url)
        "apiKey" -> resources.getString(R.string.openai_field_api_key)
        "modelId" -> resources.getString(R.string.openai_field_model_id)
        else -> this
    }
}

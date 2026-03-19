package com.letr.chatui.network.chatcompletions

import android.content.res.Resources
import com.letr.chatui.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import kotlin.coroutines.cancellation.CancellationException

@RunWith(RobolectricTestRunner::class)
class OpenAiChatCompletionContractTest {
    private lateinit var resources: Resources

    @Before
    fun setUp() {
        resources = RuntimeEnvironment.getApplication().resources
    }

    @Test
    fun `config validator isolates missing fields and invalid base urls`() {
        assertEquals(
            OpenAiChatCompletionFailure.MissingConfiguration(fieldName = "apiKey"),
            OpenAiProviderConfigValidator.validate(
                OpenAiProviderConfig(
                    baseUrl = "https://example.invalid",
                    apiKey = null,
                    modelId = "demo-model",
                )
            )
        )

        assertEquals(
            OpenAiChatCompletionFailure.InvalidBaseUrl(rawValue = "not-a-url"),
            OpenAiProviderConfigValidator.validate(
                OpenAiProviderConfig(
                    baseUrl = "not-a-url",
                    apiKey = "secret",
                    modelId = "demo-model",
                )
            )
        )

        assertEquals(
            OpenAiChatCompletionFailure.InvalidBaseUrl(rawValue = ":"),
            OpenAiProviderConfigValidator.validate(
                OpenAiProviderConfig(
                    baseUrl = ":",
                    apiKey = "secret",
                    modelId = "demo-model",
                )
            )
        )

        assertNull(
            OpenAiProviderConfigValidator.validate(
                OpenAiProviderConfig(
                    baseUrl = "https://example.invalid/v1/",
                    apiKey = "secret",
                    modelId = "demo-model",
                )
            )
        )
    }

    @Test
    fun `error mapper locks the expected http status boundaries`() {
        assertEquals(
            OpenAiChatCompletionFailure.Unauthorized,
            OpenAiChatCompletionErrorMapper.fromHttpStatus(statusCode = 401),
        )
        assertEquals(
            OpenAiChatCompletionFailure.RateLimited(retryAfterSeconds = 30),
            OpenAiChatCompletionErrorMapper.fromHttpStatus(
                statusCode = 429,
                retryAfterSeconds = 30,
            ),
        )
    }

    @Test
    fun `error mapper separates timeout and cancellation semantics`() {
        assertEquals(
            OpenAiChatCompletionFailure.Timeout,
            OpenAiChatCompletionErrorMapper.fromThrowable(SocketTimeoutException("timed out")),
        )
        assertEquals(
            OpenAiChatCompletionFailure.Timeout,
            OpenAiChatCompletionErrorMapper.fromThrowable(InterruptedIOException("timed out")),
        )
        assertEquals(
            OpenAiChatCompletionFailure.Cancelled,
            OpenAiChatCompletionErrorMapper.fromThrowable(CancellationException("caller cancelled")),
        )
    }

    @Test
    fun `ui label and message mapping stays deterministic for locked failures`() {
        assertEquals(
            resources.getString(R.string.openai_failure_label_settings_required),
            OpenAiChatCompletionFailure.MissingConfiguration("apiKey").toUiLabel(resources),
        )
        assertEquals(
            resources.getString(
                R.string.openai_failure_message_missing_configuration,
                resources.getString(R.string.openai_field_api_key),
            ),
            OpenAiChatCompletionFailure.MissingConfiguration("apiKey").toUiMessage(resources),
        )
        assertEquals(
            resources.getString(R.string.openai_failure_label_invalid_url),
            OpenAiChatCompletionFailure.InvalidBaseUrl("not-a-url").toUiLabel(resources),
        )
        assertEquals(
            resources.getString(R.string.openai_failure_label_timeout),
            OpenAiChatCompletionFailure.Timeout.toUiLabel(resources),
        )
        assertEquals(
            resources.getString(R.string.openai_failure_label_unauthorized),
            OpenAiChatCompletionFailure.Unauthorized.toUiLabel(resources),
        )
        assertEquals(
            resources.getString(R.string.openai_failure_label_rate_limited),
            OpenAiChatCompletionFailure.RateLimited(retryAfterSeconds = 12).toUiLabel(resources),
        )
        assertEquals(
            resources.getString(R.string.openai_failure_label_protocol),
            OpenAiChatCompletionFailure.Protocol("bad json").toUiLabel(resources),
        )
    }
}

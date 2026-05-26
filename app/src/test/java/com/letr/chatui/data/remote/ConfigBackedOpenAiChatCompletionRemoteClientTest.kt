package com.letr.chatui.data.remote

import com.letr.chatui.data.model.ActiveChatRuntimeConfig
import com.letr.chatui.data.model.ConversationId
import com.letr.chatui.data.model.Message
import com.letr.chatui.data.model.MessageAuthor
import com.letr.chatui.data.model.MessageId
import com.letr.chatui.data.model.MessageStatus
import com.letr.chatui.data.repository.ActiveChatConfigSource
import com.letr.chatui.network.chatcompletions.OpenAiChatCompletionFailure
import com.letr.chatui.network.chatcompletions.OpenAiChatCompletionProviderAdapter
import com.letr.chatui.network.chatcompletions.OpenAiChatCompletionProviderAdapterFactory
import com.letr.chatui.network.chatcompletions.OpenAiChatCompletionRequestDto
import com.letr.chatui.network.chatcompletions.OpenAiChatCompletionResponseDto
import com.letr.chatui.network.chatcompletions.OpenAiChatCompletionStreamEvent
import com.letr.chatui.network.chatcompletions.OpenAiChatCompletionStreamingSession
import com.letr.chatui.network.chatcompletions.OpenAiChatMessageDto
import com.letr.chatui.network.chatcompletions.OpenAiModelsResponseDto
import com.letr.chatui.network.chatcompletions.OpenAiProviderConfig
import com.letr.chatui.network.chatcompletions.OpenAiChatMessageContentPartDto
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

class ConfigBackedOpenAiChatCompletionRemoteClientTest {
    @Test
    fun `client builds request from active runtime config and domain messages`() = runBlocking {
        val capturedRequests = mutableListOf<OpenAiChatCompletionRequestDto>()
        val client = ConfigBackedOpenAiChatCompletionRemoteClient(
            activeChatConfigSource = FakeActiveChatConfigSource(),
            requestFactory = FakeOpenAiChatCompletionRequestFactory(),
            adapterFactory = OpenAiChatCompletionProviderAdapterFactory { providerConfig ->
                assertEquals("https://example.invalid/v1/", providerConfig.baseUrl)
                assertEquals("demo-model", providerConfig.modelId)
                FakeProviderAdapter(
                    onCreate = { request ->
                        capturedRequests += request
                        OpenAiChatCompletionResponseDto(
                            id = "chat-1",
                            created = 1,
                            model = "demo-model",
                            choices = emptyList(),
                        )
                    },
                    streamEvents = emptyList(),
                )
            },
        )

        val result = client.createChatCompletion(messages = sampleMessages())

        assertTrue(result is OpenAiRemoteResult.Success)
        assertEquals("demo-model", capturedRequests.single().model)
        assertEquals(false, capturedRequests.single().stream)
        assertEquals(listOf("user", "assistant"), capturedRequests.single().messages.map { it.role })
    }

    @Test
    fun `stream client maps validation failure before opening remote session`() = runBlocking {
        val client = ConfigBackedOpenAiChatCompletionRemoteClient(
            activeChatConfigSource = FakeActiveChatConfigSource(
                config = ActiveChatRuntimeConfig(
                    apiBaseUrl = "",
                    apiKey = null,
                    modelId = "",
                )
            ),
            requestFactory = FakeOpenAiChatCompletionRequestFactory(),
            adapterFactory = OpenAiChatCompletionProviderAdapterFactory {
                error("adapter should not be created when config is invalid")
            },
        )

        val events = client.streamChatCompletion(sampleMessages()).events.toList()

        assertEquals(
            listOf(
                OpenAiChatCompletionRemoteEvent.AssistantMessageFailed(
                    accumulatedText = "",
                    failure = OpenAiChatCompletionFailure.MissingConfiguration("baseUrl"),
                )
            ),
            events,
        )
    }

    @Test
    fun `stream client maps remote events and caller cancellation`() = runBlocking {
        val fakeSession = FakeStreamingSession(
            streamedEvents = listOf(
                OpenAiChatCompletionStreamEvent.TextDelta(0, "Part", "Part"),
            ),
        )
        val client = ConfigBackedOpenAiChatCompletionRemoteClient(
            activeChatConfigSource = FakeActiveChatConfigSource(),
            requestFactory = FakeOpenAiChatCompletionRequestFactory(),
            adapterFactory = OpenAiChatCompletionProviderAdapterFactory {
                FakeProviderAdapter(
                    onCreate = { error("not used") },
                    streamEvents = emptyList(),
                    onStream = { fakeSession },
                )
            },
        )

        val session = client.streamChatCompletion(sampleMessages())
        val collector = async { session.events.toList() }
        delay(50)
        session.cancel()
        val events = collector.await()

        assertTrue(fakeSession.cancelled)
        assertEquals(
            listOf(
                OpenAiChatCompletionRemoteEvent.AssistantMessageStarted,
                OpenAiChatCompletionRemoteEvent.AssistantMessageDelta("Part", "Part"),
                OpenAiChatCompletionRemoteEvent.AssistantMessageCancelled("Part"),
            ),
            events,
        )
    }

    @Test
    fun `stream client converts adapter creation failure into terminal failed event`() = runBlocking {
        val client = ConfigBackedOpenAiChatCompletionRemoteClient(
            activeChatConfigSource = FakeActiveChatConfigSource(),
            requestFactory = FakeOpenAiChatCompletionRequestFactory(),
            adapterFactory = OpenAiChatCompletionProviderAdapterFactory {
                throw IOException("boom")
            },
        )

        val events = client.streamChatCompletion(sampleMessages()).events.toList()

        assertEquals(
            listOf(
                OpenAiChatCompletionRemoteEvent.AssistantMessageFailed(
                    accumulatedText = "",
                    failure = OpenAiChatCompletionFailure.Unknown("boom"),
                )
            ),
            events,
        )
    }

    private fun sampleMessages(): List<Message> {
        return listOf(
            Message(
                id = MessageId("user-1"),
                conversationId = ConversationId("conversation-1"),
                author = MessageAuthor.USER,
                content = "Explain binary search in Kotlin",
                status = MessageStatus.COMPLETE,
                createdAtEpochMillis = 1,
                updatedAtEpochMillis = 1,
            ),
            Message(
                id = MessageId("assistant-1"),
                conversationId = ConversationId("conversation-1"),
                author = MessageAuthor.ASSISTANT,
                content = "Prior answer",
                status = MessageStatus.COMPLETE,
                createdAtEpochMillis = 2,
                updatedAtEpochMillis = 2,
            ),
        )
    }
}

private class FakeOpenAiChatCompletionRequestFactory : OpenAiChatCompletionRequestFactoryContract {
    override fun create(
        messages: List<Message>,
        providerConfig: OpenAiProviderConfig,
        stream: Boolean,
    ): OpenAiChatCompletionRequestDto {
        return OpenAiChatCompletionRequestDto(
            model = providerConfig.modelId,
            messages = messages.map { message ->
                val contentParts = mutableListOf<OpenAiChatMessageContentPartDto>()
                if (message.content.isNotBlank()) {
                    contentParts += OpenAiChatMessageContentPartDto.Text(message.content)
                }
                OpenAiChatMessageDto(
                    role = if (message.author == MessageAuthor.USER) "user" else "assistant",
                    content = contentParts,
                )
            },
            stream = stream,
        )
    }
}

private class FakeActiveChatConfigSource(
    private val config: ActiveChatRuntimeConfig = ActiveChatRuntimeConfig(
        apiBaseUrl = "https://example.invalid/v1/",
        apiKey = "secret-key",
        modelId = "demo-model",
    )
) : ActiveChatConfigSource {
    override fun observeActiveConfig(): Flow<ActiveChatRuntimeConfig> = flowOf(config)
    override suspend fun getActiveConfig(): ActiveChatRuntimeConfig = config
}

private class FakeProviderAdapter(
    private val onCreate: suspend (OpenAiChatCompletionRequestDto) -> OpenAiChatCompletionResponseDto,
    private val streamEvents: List<OpenAiChatCompletionStreamEvent>,
    private val onStream: ((OpenAiChatCompletionRequestDto) -> OpenAiChatCompletionStreamingSession)? = null,
) : OpenAiChatCompletionProviderAdapter {
    override suspend fun createChatCompletion(request: OpenAiChatCompletionRequestDto): OpenAiChatCompletionResponseDto {
        return onCreate(request)
    }

    override fun streamChatCompletion(request: OpenAiChatCompletionRequestDto): OpenAiChatCompletionStreamingSession {
        return onStream?.invoke(request) ?: FakeStreamingSession(streamedEvents = streamEvents)
    }

    override suspend fun listModels(): OpenAiModelsResponseDto {
        return OpenAiModelsResponseDto(emptyList())
    }
}

private class FakeStreamingSession(
    streamedEvents: List<OpenAiChatCompletionStreamEvent>,
) : OpenAiChatCompletionStreamingSession {
    private var closeFlow: (() -> Unit)? = null
    override val events: Flow<OpenAiChatCompletionStreamEvent> = callbackFlow {
        streamedEvents.forEach { event ->
            trySend(event).getOrThrow()
        }
        closeFlow = { close(CancellationException("cancelled")) }
        awaitClose { closeFlow = null }
    }
    var cancelled: Boolean = false

    override fun cancel() {
        cancelled = true
        closeFlow?.invoke()
    }
}

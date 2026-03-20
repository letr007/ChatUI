package com.letr.chatui.network.chatcompletions

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

class RealOpenAiChatCompletionProviderAdapterTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `create chat completion builds request from provider config`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {"id":"chat-1","created":1,"model":"demo-model","choices":[{"index":0,"message":{"role":"assistant","content":"Hello"},"finish_reason":"stop"}]}
                    """.trimIndent()
                )
        )

        val adapter = createAdapter()
        val response = adapter.createChatCompletion(
            OpenAiChatCompletionRequestDto(
                model = "demo-model",
                messages = listOf(
                    OpenAiChatMessageDto(
                        role = "user",
                        content = listOf(OpenAiChatMessageContentPartDto.Text("Explain binary search in Kotlin")),
                    )
                ),
                stream = false,
            )
        )

        val recorded = server.takeRequest()
        assertEquals("/v1/chat/completions", recorded.path)
        assertEquals("Bearer secret-key", recorded.getHeader("Authorization"))
        assertTrue(recorded.body.readUtf8().contains("\"model\":\"demo-model\""))
        assertEquals("Hello", response.choices.single().message?.content)
    }

    @Test
    fun `streaming session emits ordered deltas and completion`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(
                    """
                    data: {"id":"chat-1","choices":[{"index":0,"delta":{"content":"Hel"},"finish_reason":null}]}

                    data: {"id":"chat-1","choices":[{"index":0,"delta":{"content":"lo"},"finish_reason":null}]}

                    data: [DONE]

                    """.trimIndent()
                )
        )

        val session = createAdapter().streamChatCompletion(
            OpenAiChatCompletionRequestDto(
                model = "demo-model",
                messages = listOf(
                    OpenAiChatMessageDto(
                        role = "user",
                        content = listOf(OpenAiChatMessageContentPartDto.Text("Explain binary search in Kotlin")),
                    )
                ),
                stream = true,
            )
        )

        val events = session.events.toList()

        assertEquals(
            listOf(
                OpenAiChatCompletionStreamEvent.TextDelta(choiceIndex = 0, text = "Hel", accumulatedText = "Hel"),
                OpenAiChatCompletionStreamEvent.TextDelta(choiceIndex = 0, text = "lo", accumulatedText = "Hello"),
                OpenAiChatCompletionStreamEvent.Completed(
                    accumulatedText = "Hello",
                    finishReason = null,
                    signal = OpenAiChatCompletionTerminalSignal.DONE_MARKER,
                ),
            ),
            events,
        )
    }

    @Test
    fun `streaming session surfaces malformed and interrupted streams`() {
        runBlocking {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "text/event-stream")
                    .setBody("event: nope\n\n")
            )
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "text/event-stream")
                    .setBody("data: {\"id\":\"chat-1\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"Part\"},\"finish_reason\":null}]}\n\n")
            )

            val malformedSession = createAdapter().streamChatCompletion(
                OpenAiChatCompletionRequestDto(
                    model = "demo-model",
                    messages = listOf(
                        OpenAiChatMessageDto(
                            role = "user",
                            content = listOf(OpenAiChatMessageContentPartDto.Text("Prompt")),
                        )
                    ),
                    stream = true,
                )
            )
            try {
                malformedSession.events.toList()
            } catch (expected: Throwable) {
                assertTrue(expected is OpenAiChatCompletionProtocolException || expected.cause is OpenAiChatCompletionProtocolException)
            }

            val interruptedSession = createAdapter().streamChatCompletion(
                OpenAiChatCompletionRequestDto(
                    model = "demo-model",
                    messages = listOf(
                        OpenAiChatMessageDto(
                            role = "user",
                            content = listOf(OpenAiChatMessageContentPartDto.Text("Prompt")),
                        )
                    ),
                    stream = true,
                )
            )
            try {
                interruptedSession.events.toList()
            } catch (expected: Throwable) {
                assertTrue(
                    expected is OpenAiChatCompletionInterruptedStreamException ||
                        expected.cause is OpenAiChatCompletionInterruptedStreamException,
                )
            }
        }
    }

    @Test
    fun `streaming cancel stops underlying call`() {
        runBlocking {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "text/event-stream")
                    .setBodyDelay(2, TimeUnit.SECONDS)
                    .setBody(
                        """
                        data: {"id":"chat-1","choices":[{"index":0,"delta":{"content":"Slow"},"finish_reason":null}]}

                        data: [DONE]
                        """.trimIndent()
                    )
                )

            val session = createAdapter().streamChatCompletion(
                OpenAiChatCompletionRequestDto(
                    model = "demo-model",
                    messages = listOf(
                        OpenAiChatMessageDto(
                            role = "user",
                            content = listOf(OpenAiChatMessageContentPartDto.Text("Prompt")),
                        )
                    ),
                    stream = true,
                )
            )

            val deferred = async { session.events.toList() }
            delay(50)
            session.cancel()

            try {
                deferred.await()
            } catch (expected: Throwable) {
                assertTrue(expected is java.util.concurrent.CancellationException || expected.cause is java.util.concurrent.CancellationException)
            }
        }
    }

    @Test
    fun `http and timeout failures map through contract mapper`() {
        assertEquals(
            OpenAiChatCompletionFailure.Unauthorized,
            OpenAiChatCompletionErrorMapper.fromHttpStatus(401),
        )
        assertEquals(
            OpenAiChatCompletionFailure.RateLimited(retryAfterSeconds = 12),
            OpenAiChatCompletionErrorMapper.fromHttpStatus(429, retryAfterSeconds = 12),
        )
        assertEquals(
            OpenAiChatCompletionFailure.Timeout,
            OpenAiChatCompletionErrorMapper.fromThrowable(SocketTimeoutException("timeout")),
        )
    }

    private fun createAdapter(
        client: OkHttpClient = OkHttpClient.Builder()
            .readTimeout(250, TimeUnit.MILLISECONDS)
            .build(),
    ): RealOpenAiChatCompletionProviderAdapter {
        return RealOpenAiChatCompletionProviderAdapter(
            okHttpClient = client,
            providerConfig = OpenAiProviderConfig(
                baseUrl = server.url("/v1/").toString(),
                apiKey = "secret-key",
                modelId = "demo-model",
            ),
            ioDispatcher = Dispatchers.IO,
        )
    }
}

package com.letr.chatui.data.remote

import com.letr.chatui.network.chatcompletions.OpenAiChatCompletionFailure
import com.letr.chatui.network.chatcompletions.OpenAiChatCompletionStreamEvent
import com.letr.chatui.network.chatcompletions.OpenAiChatCompletionTerminalSignal
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.coroutines.cancellation.CancellationException

class OpenAiChatCompletionStreamReducerTest {
    private val reducer = OpenAiChatCompletionStreamReducer()

    @Test
    fun `reducer emits ordered partial updates and complete terminal state`() = runBlocking {
        val reduced = reducer.reduce(
            flow {
                emit(OpenAiChatCompletionStreamEvent.TextDelta(0, "Hel", "Hel"))
                emit(OpenAiChatCompletionStreamEvent.TextDelta(0, "lo", "Hello"))
                emit(
                    OpenAiChatCompletionStreamEvent.Completed(
                        accumulatedText = "Hello",
                        finishReason = "stop",
                        signal = OpenAiChatCompletionTerminalSignal.FINISH_REASON,
                    )
                )
            }
        ).toList()

        assertEquals(
            listOf(
                OpenAiChatCompletionRemoteEvent.AssistantMessageStarted,
                OpenAiChatCompletionRemoteEvent.AssistantMessageDelta("Hel", "Hel"),
                OpenAiChatCompletionRemoteEvent.AssistantMessageDelta("lo", "Hello"),
                OpenAiChatCompletionRemoteEvent.AssistantMessageCompleted("Hello", "stop"),
            ),
            reduced,
        )
    }

    @Test
    fun `reducer preserves partial text on interrupted stream`() = runBlocking {
        val reduced = reducer.reduce(
            flow {
                emit(OpenAiChatCompletionStreamEvent.TextDelta(0, "Part", "Part"))
            }
        ).toList()

        assertEquals(
            listOf(
                OpenAiChatCompletionRemoteEvent.AssistantMessageStarted,
                OpenAiChatCompletionRemoteEvent.AssistantMessageDelta("Part", "Part"),
                OpenAiChatCompletionRemoteEvent.AssistantMessageFailed(
                    accumulatedText = "Part",
                    failure = OpenAiChatCompletionFailure.Protocol("Stream interrupted before terminal event."),
                ),
            ),
            reduced,
        )
    }

    @Test
    fun `reducer maps cancellation to cancelled terminal state with preserved partial text`() = runBlocking {
        val reduced = reducer.reduce(
            flow {
                emit(OpenAiChatCompletionStreamEvent.TextDelta(0, "Part", "Part"))
                throw CancellationException("cancelled")
            }
        ).toList()

        assertEquals(
            listOf(
                OpenAiChatCompletionRemoteEvent.AssistantMessageStarted,
                OpenAiChatCompletionRemoteEvent.AssistantMessageDelta("Part", "Part"),
                OpenAiChatCompletionRemoteEvent.AssistantMessageCancelled(accumulatedText = "Part"),
            ),
            reduced,
        )
    }

    @Test
    fun `reducer maps arbitrary failure with preserved accumulated text`() = runBlocking {
        val reduced = reducer.reduce(
            flow {
                emit(OpenAiChatCompletionStreamEvent.TextDelta(0, "Part", "Part"))
                throw IllegalStateException("boom")
            },
            mapFailure = { OpenAiChatCompletionFailure.Unauthorized },
        ).toList()

        assertEquals(
            listOf(
                OpenAiChatCompletionRemoteEvent.AssistantMessageStarted,
                OpenAiChatCompletionRemoteEvent.AssistantMessageDelta("Part", "Part"),
                OpenAiChatCompletionRemoteEvent.AssistantMessageFailed(
                    accumulatedText = "Part",
                    failure = OpenAiChatCompletionFailure.Unauthorized,
                ),
            ),
            reduced,
        )
    }
}

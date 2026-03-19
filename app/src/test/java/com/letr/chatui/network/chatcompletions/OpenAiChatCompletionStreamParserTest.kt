package com.letr.chatui.network.chatcompletions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiChatCompletionStreamParserTest {
    @Test
    fun `parser emits delta chunks and terminal finish semantics`() {
        val parser = OpenAiChatCompletionStreamParser()

        val firstEvents = parser.parse(
            """data: {"id":"chat-1","created":1,"model":"demo-model","choices":[{"index":0,"delta":{"role":"assistant","content":"Hel"},"finish_reason":null}]}"""
        )
        val secondEvents = parser.parse(
            """data: {"id":"chat-1","created":2,"model":"demo-model","choices":[{"index":0,"delta":{"content":"lo"},"finish_reason":null}]}"""
        )
        val thirdEvents = parser.parse(
            """data: {"id":"chat-1","created":3,"model":"demo-model","choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}"""
        )

        assertEquals(
            listOf(
                OpenAiChatCompletionStreamEvent.TextDelta(
                    choiceIndex = 0,
                    text = "Hel",
                    accumulatedText = "Hel",
                )
            ),
            firstEvents,
        )
        assertEquals(
            listOf(
                OpenAiChatCompletionStreamEvent.TextDelta(
                    choiceIndex = 0,
                    text = "lo",
                    accumulatedText = "Hello",
                )
            ),
            secondEvents,
        )
        assertEquals(
            listOf(
                OpenAiChatCompletionStreamEvent.Completed(
                    accumulatedText = "Hello",
                    finishReason = "stop",
                    signal = OpenAiChatCompletionTerminalSignal.FINISH_REASON,
                )
            ),
            thirdEvents,
        )

        assertEquals(
            OpenAiChatCompletionStreamSnapshot(
                accumulatedText = "Hello",
                isTerminal = true,
                finishReason = "stop",
                terminalSignal = OpenAiChatCompletionTerminalSignal.FINISH_REASON,
            ),
            parser.snapshot(),
        )
    }

    @Test
    fun `parser honors done marker without duplicating terminal events`() {
        val parser = OpenAiChatCompletionStreamParser()

        parser.parse(
            """data: {"id":"chat-2","choices":[{"index":0,"delta":{"content":"Partial"},"finish_reason":null}]}"""
        )

        val doneEvents = parser.parse("data: [DONE]")
        val trailingDoneEvents = parser.parse("data: [DONE]")

        assertEquals(
            listOf(
                OpenAiChatCompletionStreamEvent.Completed(
                    accumulatedText = "Partial",
                    finishReason = null,
                    signal = OpenAiChatCompletionTerminalSignal.DONE_MARKER,
                )
            ),
            doneEvents,
        )
        assertTrue(trailingDoneEvents.isEmpty())
        assertEquals("Partial", parser.snapshot().accumulatedText)
        assertEquals(OpenAiChatCompletionTerminalSignal.DONE_MARKER, parser.snapshot().terminalSignal)
    }

    @Test
    fun `parser preserves partial text when malformed chunk interrupts the stream`() {
        val parser = OpenAiChatCompletionStreamParser()

        parser.parse(
            """data: {"id":"chat-3","choices":[{"index":0,"delta":{"content":"Partial"},"finish_reason":null}]}"""
        )

        try {
            parser.parse("""data: {"id":"chat-3","choices":[{"index":0,"delta":{"content":"broken"},"finish_reason":null}]""")
        } catch (expected: IllegalArgumentException) {
            assertEquals("Malformed chat completion stream chunk.", expected.message)
        }

        assertEquals("Partial", parser.snapshot().accumulatedText)
        assertFalse(parser.snapshot().isTerminal)
    }

    @Test
    fun `parser snapshot keeps accumulated text when stream stops before terminal markers`() {
        val parser = OpenAiChatCompletionStreamParser()

        parser.parse(
            """data: {"id":"chat-4","choices":[{"index":0,"delta":{"content":"Hel"},"finish_reason":null}]}"""
        )
        parser.parse(
            """data: {"id":"chat-4","choices":[{"index":0,"delta":{"content":"lo"},"finish_reason":null}]}"""
        )

        val snapshot = parser.snapshot()

        assertEquals("Hello", snapshot.accumulatedText)
        assertFalse(snapshot.isTerminal)
        assertEquals(null, snapshot.finishReason)
    }
}

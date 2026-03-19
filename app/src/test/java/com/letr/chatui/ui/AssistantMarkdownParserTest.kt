package com.letr.chatui.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantMarkdownParserTest {
    @Test
    fun `parse splits paragraphs and fenced code blocks`() {
        val document = AssistantMarkdownParser.parse(
            """
            First paragraph.

            ```kotlin
            val answer = 42
            println(answer)
            ```

            Second paragraph with `inline code`.
            """.trimIndent()
        )

        assertEquals(
            listOf(
                MarkdownBlock.Paragraph("First paragraph."),
                MarkdownBlock.CodeFence(
                    code = "val answer = 42\nprintln(answer)",
                    language = "kotlin",
                ),
                MarkdownBlock.Paragraph("Second paragraph with `inline code`."),
            ),
            document.blocks,
        )
    }

    @Test
    fun `parse treats unclosed fence as code block until end`() {
        val document = AssistantMarkdownParser.parse(
            """
            Before

            ```
            line one
            line two
            """.trimIndent()
        )

        assertEquals(
            listOf(
                MarkdownBlock.Paragraph("Before"),
                MarkdownBlock.CodeFence(
                    code = "line one\nline two",
                    language = null,
                ),
            ),
            document.blocks,
        )
    }

    @Test
    fun `parse returns empty document for blank input`() {
        val document = AssistantMarkdownParser.parse("   \n\n")

        assertTrue(document.blocks.isEmpty())
    }
}

package com.letr.chatui.ui

data class MarkdownDocument(
    val blocks: List<MarkdownBlock>,
)

sealed interface MarkdownBlock {
    data class Paragraph(val text: String) : MarkdownBlock

    data class CodeFence(
        val code: String,
        val language: String?,
    ) : MarkdownBlock
}

internal object AssistantMarkdownParser {
    fun parse(source: String): MarkdownDocument {
        if (source.isBlank()) {
            return MarkdownDocument(emptyList())
        }

        val lines = source.split("\n")
        val blocks = mutableListOf<MarkdownBlock>()
        val paragraphLines = mutableListOf<String>()
        var lineIndex = 0

        fun flushParagraph() {
            if (paragraphLines.isEmpty()) {
                return
            }

            val paragraphText = paragraphLines.joinToString(separator = "\n") { it.trimEnd() }.trim()
            if (paragraphText.isNotEmpty()) {
                blocks += MarkdownBlock.Paragraph(paragraphText)
            }
            paragraphLines.clear()
        }

        while (lineIndex < lines.size) {
            val line = lines[lineIndex]
            val trimmedLine = line.trim()

            if (trimmedLine.startsWith("```")) {
                flushParagraph()
                val language = trimmedLine.removePrefix("```").trim().ifEmpty { null }
                val codeLines = mutableListOf<String>()
                lineIndex += 1

                while (lineIndex < lines.size && !lines[lineIndex].trim().startsWith("```")) {
                    codeLines += lines[lineIndex]
                    lineIndex += 1
                }

                blocks += MarkdownBlock.CodeFence(
                    code = codeLines.joinToString(separator = "\n"),
                    language = language,
                )

                if (lineIndex < lines.size && lines[lineIndex].trim().startsWith("```")) {
                    lineIndex += 1
                }
                continue
            }

            if (trimmedLine.isBlank()) {
                flushParagraph()
                lineIndex += 1
                continue
            }

            paragraphLines += line
            lineIndex += 1
        }

        flushParagraph()
        return MarkdownDocument(blocks)
    }
}

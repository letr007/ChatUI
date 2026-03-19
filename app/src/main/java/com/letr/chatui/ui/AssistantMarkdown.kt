package com.letr.chatui.ui

data class MarkdownDocument(
    val blocks: List<MarkdownBlock>,
)

sealed interface MarkdownBlock {
    data class Paragraph(val text: String) : MarkdownBlock

    data class Heading(
        val level: Int,
        val text: String,
    ) : MarkdownBlock

    data class BulletList(val items: List<String>) : MarkdownBlock

    data class OrderedList(val items: List<String>) : MarkdownBlock

    data class BlockQuote(val text: String) : MarkdownBlock

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

            val headingMatch = Regex("^(#{1,6})\\s+(.+)$").matchEntire(trimmedLine)
            if (headingMatch != null) {
                flushParagraph()
                blocks += MarkdownBlock.Heading(
                    level = headingMatch.groupValues[1].length,
                    text = headingMatch.groupValues[2].trim(),
                )
                lineIndex += 1
                continue
            }

            if (trimmedLine.startsWith(">")) {
                flushParagraph()
                val quoteLines = mutableListOf<String>()
                while (lineIndex < lines.size && lines[lineIndex].trim().startsWith(">")) {
                    quoteLines += lines[lineIndex].trim().removePrefix(">").trimStart()
                    lineIndex += 1
                }
                val quoteText = quoteLines.joinToString(separator = "\n").trim()
                if (quoteText.isNotEmpty()) {
                    blocks += MarkdownBlock.BlockQuote(quoteText)
                }
                continue
            }

            if (Regex("^[-*+]\\s+.+$").matches(trimmedLine)) {
                flushParagraph()
                val items = mutableListOf<String>()
                while (lineIndex < lines.size && Regex("^[-*+]\\s+.+$").matches(lines[lineIndex].trim())) {
                    items += lines[lineIndex].trim().removeRange(0, 2).trimStart()
                    lineIndex += 1
                }
                if (items.isNotEmpty()) {
                    blocks += MarkdownBlock.BulletList(items)
                }
                continue
            }

            if (Regex("^\\d+\\.\\s+.+$").matches(trimmedLine)) {
                flushParagraph()
                val items = mutableListOf<String>()
                while (lineIndex < lines.size && Regex("^\\d+\\.\\s+.+$").matches(lines[lineIndex].trim())) {
                    val current = lines[lineIndex].trim()
                    items += current.substringAfter('.').trimStart()
                    lineIndex += 1
                }
                if (items.isNotEmpty()) {
                    blocks += MarkdownBlock.OrderedList(items)
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

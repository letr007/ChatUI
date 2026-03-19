package com.letr.chatui.network.chatcompletions

import com.squareup.moshi.JsonReader
import okio.Buffer

class OpenAiChatCompletionStreamParser {
    private val accumulatedText = StringBuilder()
    private var finishReason: String? = null
    private var terminalSignal: OpenAiChatCompletionTerminalSignal? = null

    fun parse(rawLine: String): List<OpenAiChatCompletionStreamEvent> {
        val line = rawLine.trim()
        if (line.isBlank()) {
            return emptyList()
        }
        require(line.startsWith("data:")) {
            "Expected an SSE data line for chat completions."
        }

        val payload = line.removePrefix("data:").trim()
        if (payload == "[DONE]") {
            return emitCompleted(signal = OpenAiChatCompletionTerminalSignal.DONE_MARKER, finishReason = finishReason)
        }

        val chunk = parseChunk(payload)
        val events = mutableListOf<OpenAiChatCompletionStreamEvent>()
        for (choice in chunk.choices) {
            val content = choice.delta.content
            if (!content.isNullOrEmpty()) {
                accumulatedText.append(content)
                events += OpenAiChatCompletionStreamEvent.TextDelta(
                    choiceIndex = choice.index,
                    text = content,
                    accumulatedText = accumulatedText.toString(),
                )
            }

            if (!choice.finishReason.isNullOrBlank()) {
                events += emitCompleted(
                    signal = OpenAiChatCompletionTerminalSignal.FINISH_REASON,
                    finishReason = choice.finishReason,
                )
            }
        }

        return events
    }

    fun snapshot(): OpenAiChatCompletionStreamSnapshot {
        return OpenAiChatCompletionStreamSnapshot(
            accumulatedText = accumulatedText.toString(),
            isTerminal = terminalSignal != null,
            finishReason = finishReason,
            terminalSignal = terminalSignal,
        )
    }

    private fun emitCompleted(
        signal: OpenAiChatCompletionTerminalSignal,
        finishReason: String?,
    ): List<OpenAiChatCompletionStreamEvent.Completed> {
        if (terminalSignal != null) {
            return emptyList()
        }

        terminalSignal = signal
        this.finishReason = finishReason ?: this.finishReason
        return listOf(
            OpenAiChatCompletionStreamEvent.Completed(
                accumulatedText = accumulatedText.toString(),
                finishReason = this.finishReason,
                signal = signal,
            )
        )
    }

    private fun parseChunk(payload: String): OpenAiChatCompletionChunkDto {
        val reader = JsonReader.of(Buffer().writeUtf8(payload))

        return try {
            var id: String? = null
            var created: Long? = null
            var model: String? = null
            val choices = mutableListOf<OpenAiChatCompletionChunkChoiceDto>()

            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "id" -> id = reader.nextNullableString()
                    "created" -> created = reader.nextNullableLong()
                    "model" -> model = reader.nextNullableString()
                    "choices" -> {
                        reader.beginArray()
                        while (reader.hasNext()) {
                            choices += reader.readChoice()
                        }
                        reader.endArray()
                    }

                    else -> reader.skipValue()
                }
            }
            reader.endObject()

            OpenAiChatCompletionChunkDto(
                id = id,
                created = created,
                model = model,
                choices = choices,
            )
        } catch (exception: Exception) {
            throw IllegalArgumentException("Malformed chat completion stream chunk.", exception)
        } finally {
            reader.close()
        }
    }
}

private fun JsonReader.readChoice(): OpenAiChatCompletionChunkChoiceDto {
    var index = 0
    var delta = OpenAiChatCompletionDeltaDto()
    var finishReason: String? = null

    beginObject()
    while (hasNext()) {
        when (nextName()) {
            "index" -> index = nextInt()
            "delta" -> delta = readDelta()
            "finish_reason" -> finishReason = nextNullableString()
            else -> skipValue()
        }
    }
    endObject()

    return OpenAiChatCompletionChunkChoiceDto(
        index = index,
        delta = delta,
        finishReason = finishReason,
    )
}

private fun JsonReader.readDelta(): OpenAiChatCompletionDeltaDto {
    var role: String? = null
    var content: String? = null

    beginObject()
    while (hasNext()) {
        when (nextName()) {
            "role" -> role = nextNullableString()
            "content" -> content = nextNullableString()
            else -> skipValue()
        }
    }
    endObject()

    return OpenAiChatCompletionDeltaDto(role = role, content = content)
}

private fun JsonReader.nextNullableString(): String? {
    return if (peek() == JsonReader.Token.NULL) {
        nextNull<String>()
    } else {
        nextString()
    }
}

private fun JsonReader.nextNullableLong(): Long? {
    return if (peek() == JsonReader.Token.NULL) {
        nextNull<Long>()
    } else {
        nextLong()
    }
}

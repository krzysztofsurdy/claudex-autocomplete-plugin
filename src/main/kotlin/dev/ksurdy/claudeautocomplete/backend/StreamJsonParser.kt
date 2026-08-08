package dev.ksurdy.claudeautocomplete.backend

import com.google.gson.JsonObject
import com.google.gson.JsonParser

sealed interface StreamEvent {
    data class TextDelta(val text: String) : StreamEvent
    data class Result(val isError: Boolean, val text: String) : StreamEvent
    data object Other : StreamEvent
}

object StreamJsonParser {
    fun parse(line: String): StreamEvent? {
        val obj = parseObject(line) ?: return null
        return toEvent(obj)
    }

    fun parseObject(text: String): JsonObject? {
        if (text.isBlank()) return null
        return try {
            JsonParser.parseString(text).takeIf { it.isJsonObject }?.asJsonObject
        } catch (_: RuntimeException) {
            null
        }
    }

    fun toEvent(obj: JsonObject): StreamEvent = when (obj.string("type")) {
        "stream_event" -> textDelta(obj) ?: StreamEvent.Other
        "result" -> StreamEvent.Result(
            isError = obj.get("is_error")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false,
            text = obj.string("result").orEmpty(),
        )
        else -> StreamEvent.Other
    }

    private fun textDelta(obj: JsonObject): StreamEvent.TextDelta? {
        val event = obj.get("event")?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        if (event.string("type") != "content_block_delta") return null
        val delta = event.get("delta")?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        if (delta.string("type") != "text_delta") return null
        return StreamEvent.TextDelta(delta.string("text").orEmpty())
    }

    private fun JsonObject.string(name: String): String? =
        get(name)?.takeIf { it.isJsonPrimitive }?.asString
}

package dev.ksurdy.claudeautocomplete.backend

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.time.Instant

sealed interface StreamEvent {
    data class TextDelta(val text: String) : StreamEvent
    data class Result(val isError: Boolean, val text: String) : StreamEvent
    data class RateLimit(val status: String?, val fiveHour: UsageWindow?, val sevenDay: UsageWindow?) : StreamEvent
    data object Other : StreamEvent
}

object StreamJsonParser {
    private const val FIVE_HOUR_MINUTES = 300
    private const val SEVEN_DAY_MINUTES = 10080


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
        "rate_limit_event" -> rateLimit(obj)
        "result" -> StreamEvent.Result(
            isError = obj.get("is_error")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false,
            text = obj.string("result").orEmpty(),
        )
        else -> StreamEvent.Other
    }

    private fun rateLimit(obj: JsonObject): StreamEvent {
        val info = obj.get("rate_limit_info")?.takeIf { it.isJsonObject }?.asJsonObject ?: return StreamEvent.Other
        val windows = info.get("unifiedWindows")?.takeIf { it.isJsonObject }?.asJsonObject
        return StreamEvent.RateLimit(info.string("status"), window(windows, "five_hour"), window(windows, "seven_day"))
    }

    private fun window(windows: JsonObject?, name: String): UsageWindow? {
        val minutes = if (name == "five_hour") FIVE_HOUR_MINUTES else SEVEN_DAY_MINUTES
        val window = windows?.get(name)?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        val raw = window.get("utilization")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asDouble ?: return null
        val utilization = if (raw > 1.0) raw / 100.0 else raw
        val resets = window.get("resetsAt")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asLong
        return UsageWindow(utilization.coerceIn(0.0, 1.0), resets?.let(Instant::ofEpochSecond), minutes)
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

package dev.ksurdy.claudeautocomplete.backend

import com.google.gson.JsonObject
import java.time.Instant

sealed interface CodexEvent {
    data class AgentMessage(val text: String) : CodexEvent
    data object TurnCompleted : CodexEvent
    data class TurnFailed(val message: String) : CodexEvent
    data class Error(val message: String) : CodexEvent
    data object Other : CodexEvent
}

object CodexJsonlParser {
    fun parse(line: String): CodexEvent? {
        val obj = StreamJsonParser.parseObject(line) ?: return null
        return when (obj.string("type")) {
            "item.completed" -> agentMessage(obj) ?: CodexEvent.Other
            "turn.completed" -> CodexEvent.TurnCompleted
            "turn.failed" -> CodexEvent.TurnFailed(obj.obj("error")?.string("message").orEmpty())
            "error" -> CodexEvent.Error(obj.string("message").orEmpty())
            else -> CodexEvent.Other
        }
    }

    fun parseRateLimits(result: JsonObject): StreamEvent.RateLimit {
        val snapshot = result.obj("rateLimitsByLimitId")?.obj("codex") ?: result.obj("rateLimits") ?: JsonObject()
        val reached = snapshot.get("rateLimitReachedType")?.takeIf { it.isJsonPrimitive } != null
        return StreamEvent.RateLimit(
            status = if (reached) "rejected" else "allowed",
            fiveHour = window(snapshot.obj("primary")),
            sevenDay = window(snapshot.obj("secondary")),
        )
    }

    private fun window(window: JsonObject?): UsageWindow? {
        val used = window?.get("usedPercent")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asDouble ?: return null
        val resets = window.get("resetsAt")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asLong
        val minutes = window.get("windowDurationMins")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt
        return UsageWindow((used / 100.0).coerceIn(0.0, 1.0), resets?.let(Instant::ofEpochSecond), minutes)
    }

    private fun agentMessage(obj: JsonObject): CodexEvent? {
        val item = obj.obj("item") ?: return null
        if (item.string("type") != "agent_message") return null
        return CodexEvent.AgentMessage(item.string("text").orEmpty())
    }

    private fun JsonObject.string(name: String): String? = get(name)?.takeIf { it.isJsonPrimitive }?.asString

    private fun JsonObject.obj(name: String): JsonObject? = get(name)?.takeIf { it.isJsonObject }?.asJsonObject
}

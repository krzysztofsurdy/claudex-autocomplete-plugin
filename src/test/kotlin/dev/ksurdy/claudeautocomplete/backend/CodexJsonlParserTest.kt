package dev.ksurdy.claudeautocomplete.backend

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CodexJsonlParserTest {
    @Test
    fun `agent message item`() {
        val line = """{"type":"item.completed","item":{"id":"item_1","type":"agent_message","text":"return 1;"}}"""
        assertEquals(CodexEvent.AgentMessage("return 1;"), CodexJsonlParser.parse(line))
    }

    @Test
    fun `error item is ignored as a warning`() {
        val line = """{"type":"item.completed","item":{"id":"item_0","type":"error","message":"Model metadata for x not found"}}"""
        assertEquals(CodexEvent.Other, CodexJsonlParser.parse(line))
    }

    @Test
    fun `turn completed`() {
        assertEquals(CodexEvent.TurnCompleted, CodexJsonlParser.parse("""{"type":"turn.completed","usage":{"input_tokens":1}}"""))
    }

    @Test
    fun `turn failed carries message`() {
        val line = """{"type":"turn.failed","error":{"message":"unexpected status 401 Unauthorized"}}"""
        assertEquals(CodexEvent.TurnFailed("unexpected status 401 Unauthorized"), CodexJsonlParser.parse(line))
    }

    @Test
    fun `error event carries message`() {
        assertEquals(CodexEvent.Error("Reconnecting... 1/5"), CodexJsonlParser.parse("""{"type":"error","message":"Reconnecting... 1/5"}"""))
    }

    @Test
    fun `other and invalid lines`() {
        assertEquals(CodexEvent.Other, CodexJsonlParser.parse("""{"type":"thread.started","thread_id":"x"}"""))
        assertEquals(CodexEvent.Other, CodexJsonlParser.parse("""{"type":"item.started","item":{"type":"command_execution"}}"""))
        assertNull(CodexJsonlParser.parse(""))
        assertNull(CodexJsonlParser.parse("2026 ERROR not json"))
    }

    @Test
    fun `rate limits response is parsed from primary and secondary`() {
        val json = """{"rateLimits":{"limitId":"codex","primary":{"usedPercent":25,"windowDurationMins":300,"resetsAt":1791200400},"secondary":{"usedPercent":60,"windowDurationMins":10080,"resetsAt":1791302400},"rateLimitReachedType":null}}"""
        val event = CodexJsonlParser.parseRateLimits(com.google.gson.JsonParser.parseString(json).asJsonObject)
        assertEquals("allowed", event.status)
        assertEquals(UsageWindow(0.25, java.time.Instant.ofEpochSecond(1791200400)), event.fiveHour)
        assertEquals(UsageWindow(0.6, java.time.Instant.ofEpochSecond(1791302400)), event.sevenDay)
    }

    @Test
    fun `rate limit reached marks rejected and prefers codex bucket`() {
        val json = """{"rateLimits":{"primary":{"usedPercent":1}},"rateLimitsByLimitId":{"codex":{"primary":{"usedPercent":100},"rateLimitReachedType":"rate_limit_reached"}}}"""
        val event = CodexJsonlParser.parseRateLimits(com.google.gson.JsonParser.parseString(json).asJsonObject)
        assertEquals("rejected", event.status)
        assertEquals(1.0, event.fiveHour?.utilization)
        assertNull(event.sevenDay)
    }
}

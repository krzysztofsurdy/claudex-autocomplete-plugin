package dev.ksurdy.claudeautocomplete.backend

import kotlin.test.assertIs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StreamJsonParserTest {
    @Test
    fun `text delta is extracted`() {
        val line = """{"type":"stream_event","event":{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"he\"llo"}}}"""
        assertEquals(StreamEvent.TextDelta("he\"llo"), StreamJsonParser.parse(line))
    }

    @Test
    fun `thinking delta is ignored`() {
        val line = """{"type":"stream_event","event":{"type":"content_block_delta","delta":{"type":"thinking_delta","thinking":"x"}}}"""
        assertEquals(StreamEvent.Other, StreamJsonParser.parse(line))
    }

    @Test
    fun `result success`() {
        val line = """{"type":"result","subtype":"success","is_error":false,"result":"abc","usage":{}}"""
        assertEquals(StreamEvent.Result(false, "abc"), StreamJsonParser.parse(line))
    }

    @Test
    fun `result error`() {
        val line = """{"type":"result","is_error":true,"result":"Not logged in"}"""
        assertEquals(StreamEvent.Result(true, "Not logged in"), StreamJsonParser.parse(line))
    }

    @Test
    fun `result without text is empty string`() {
        assertEquals(StreamEvent.Result(false, ""), StreamJsonParser.parse("""{"type":"result","is_error":false}"""))
    }

    @Test
    fun `other events`() {
        assertEquals(StreamEvent.Other, StreamJsonParser.parse("""{"type":"system","subtype":"init"}"""))
    }

    @Test
    fun `blank and invalid lines yield null`() {
        assertNull(StreamJsonParser.parse(""))
        assertNull(StreamJsonParser.parse("   "))
        assertNull(StreamJsonParser.parse("not json"))
        assertNull(StreamJsonParser.parse("[1,2]"))
    }

    private val rateLimit = """{"type":"rate_limit_event","rate_limit_info":{"status":"allowed","resetsAt":1791200400,"rateLimitType":"five_hour","overageStatus":"rejected","isUsingOverage":false,"unifiedWindows":{"five_hour":{"utilization":0.14,"resetsAt":1791200400},"seven_day":{"utilization":0.22,"resetsAt":1791302400}}},"uuid":"u"}"""

    @Test
    fun `rate limit event is parsed with both windows`() {
        val event = assertIs<StreamEvent.RateLimit>(StreamJsonParser.parse(rateLimit))
        assertEquals("allowed", event.status)
        assertEquals(UsageWindow(0.14, java.time.Instant.ofEpochSecond(1791200400), 300), event.fiveHour)
        assertEquals(UsageWindow(0.22, java.time.Instant.ofEpochSecond(1791302400), 10080), event.sevenDay)
    }

    @Test
    fun `percentage utilization is normalized`() {
        val line = """{"type":"rate_limit_event","rate_limit_info":{"status":"allowed_warning","unifiedWindows":{"five_hour":{"utilization":85}}}}"""
        val event = assertIs<StreamEvent.RateLimit>(StreamJsonParser.parse(line))
        assertEquals(UsageWindow(0.85, null, 300), event.fiveHour)
        assertNull(event.sevenDay)
    }

    @Test
    fun `rate limit event without windows still yields status`() {
        val line = """{"type":"rate_limit_event","rate_limit_info":{"status":"rejected"}}"""
        val event = assertIs<StreamEvent.RateLimit>(StreamJsonParser.parse(line))
        assertEquals("rejected", event.status)
        assertNull(event.fiveHour)
    }
}

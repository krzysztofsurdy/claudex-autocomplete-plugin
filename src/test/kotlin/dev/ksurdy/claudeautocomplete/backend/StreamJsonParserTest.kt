package dev.ksurdy.claudeautocomplete.backend

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
}

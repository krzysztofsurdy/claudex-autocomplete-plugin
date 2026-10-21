package dev.ksurdy.claudeautocomplete.completion

import dev.ksurdy.claudeautocomplete.backend.testContext
import kotlin.test.Test
import kotlin.test.assertEquals

class CompletionReplyTest {
    private val context = testContext(prefix = "\$x = ", multiline = false)

    private fun final(raw: String) = CompletionReply.parse(raw, context, final = true)

    private fun partial(raw: String) = CompletionReply.parse(raw, context, final = false)

    @Test
    fun `content of the completion block is extracted`() {
        assertEquals(Reply("foo()", ReplySource.Tagged, closed = true), final("<completion>foo()</completion>"))
    }

    @Test
    fun `text outside the block is ignored`() {
        val raw = "The cursor sits here.\n<completion>foo()</completion>\nWait, that is not right."
        assertEquals(Reply("foo()", ReplySource.Tagged, closed = true), final(raw))
    }

    @Test
    fun `only the first block counts`() {
        assertEquals("a", final("<completion>a</completion><completion>b</completion>").content)
    }

    @Test
    fun `an empty block is an empty tagged reply`() {
        assertEquals(Reply("", ReplySource.Tagged, closed = true), final("<completion></completion>"))
    }

    @Test
    fun `whitespace and newlines inside the block are kept`() {
        assertEquals("  a();\n  b();\n", final("<completion>  a();\n  b();\n</completion>").content)
    }

    @Test
    fun `a missing closing tag takes the rest of the reply`() {
        assertEquals(Reply("foo()", ReplySource.Tagged, closed = false), final("<completion>foo()"))
    }

    @Test
    fun `a final reply drops a cut off closing tag`() {
        assertEquals("foo()", final("<completion>foo()</compl").content)
        assertEquals("foo()", final("<completion>foo()</").content)
    }

    @Test
    fun `a final reply keeps a lone angle bracket`() {
        assertEquals("a <", final("<completion>a <").content)
    }

    @Test
    fun `a streamed reply holds back every prefix of the closing tag`() {
        assertEquals("foo()", partial("<completion>foo()</compl").content)
        assertEquals("foo()", partial("<completion>foo()<").content)
        assertEquals("foo()", partial("<completion>foo()</completion").content)
        assertEquals("foo()<b", partial("<completion>foo()<b").content)
    }

    @Test
    fun `a streamed reply never shows the closing tag or what follows`() {
        val reply = partial("<completion>foo()</completion>\nWait")
        assertEquals("foo()", reply.content)
        assertEquals(true, reply.closed)
    }

    @Test
    fun `a streamed reply waits while the opening tag is still arriving`() {
        assertEquals(ReplySource.Pending, partial("<comp").source)
        assertEquals(ReplySource.Pending, partial("  <").source)
        assertEquals(ReplySource.Pending, partial("<completion").source)
        assertEquals(ReplySource.Tagged, partial("<completion>").source)
    }

    @Test
    fun `a final reply that merely starts like a tag is bare text`() {
        assertEquals(ReplySource.Bare, final("<comp").source)
    }

    @Test
    fun `a reply without tags is bare text`() {
        assertEquals(Reply("foo()", ReplySource.Bare), final("foo()"))
    }

    @Test
    fun `a bare reply that reads like prose is rejected`() {
        assertEquals(Reply("", ReplySource.ProseRejected), final("The cursor sits inside a call, so nothing fits."))
    }

    @Test
    fun `streaming starts at the tag whatever comes before it`() {
        assertEquals("a();", partial("Thinking.\n<completion>a();").content)
    }
}

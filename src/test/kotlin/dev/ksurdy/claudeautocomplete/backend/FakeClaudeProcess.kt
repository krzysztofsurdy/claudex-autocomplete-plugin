package dev.ksurdy.claudeautocomplete.backend

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel

class FakeClaudeProcess(private val onSend: FakeClaudeProcess.(String) -> Unit = {}) : ClaudeProcess {
    private val channel = Channel<String>(Channel.UNLIMITED)
    val sent = mutableListOf<String>()
    var killed = false
    var inputClosed = false

    override val lines: ReceiveChannel<String> get() = channel
    override val isAlive: Boolean get() = !killed && !channel.isClosedForReceive

    override fun send(line: String) {
        sent += line
        onSend(line)
    }

    override fun closeInput() {
        inputClosed = true
        onSend("<EOF>")
    }

    override fun kill() {
        killed = true
        channel.close()
    }

    fun emit(line: String) {
        channel.trySend(line)
    }

    fun finish() {
        channel.close()
    }

    companion object {
        fun delta(text: String) =
            """{"type":"stream_event","event":{"type":"content_block_delta","delta":{"type":"text_delta","text":${quote(text)}}}}"""

        fun result(text: String, isError: Boolean = false) =
            """{"type":"result","is_error":$isError,"result":${quote(text)}}"""

        fun quote(text: String) = com.google.gson.JsonPrimitive(text).toString()
    }
}

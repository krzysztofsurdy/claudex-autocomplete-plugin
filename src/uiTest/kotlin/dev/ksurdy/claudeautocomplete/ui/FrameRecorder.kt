package dev.ksurdy.claudeautocomplete.ui

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class RecordedFrame(val file: Path, val elapsedMs: Long)

class FrameRecorder(private val canvas: IdeCanvas, private val directory: Path, private val intervalMs: Long = 150) {
    private val frames = CopyOnWriteArrayList<RecordedFrame>()
    private val running = AtomicBoolean(false)
    private var worker: Thread? = null
    private var failure: Throwable? = null
    private var startedAt = 0L

    fun start() {
        Files.createDirectories(directory)
        startedAt = System.nanoTime()
        running.set(true)
        worker = thread(name = "frame-recorder", isDaemon = true) {
            var index = 0
            while (running.get()) {
                val tick = System.nanoTime()
                runCatching {
                    val file = directory.resolve("frame-%04d.%s".format(index++, IdeCanvas.FAST_FORMAT))
                    canvas.paint(file)
                    frames += RecordedFrame(file, (tick - startedAt) / NANOS_PER_MS)
                }.onFailure {
                    failure = it
                    running.set(false)
                }
                val spent = (System.nanoTime() - tick) / NANOS_PER_MS
                if (spent < intervalMs) Thread.sleep(intervalMs - spent)
            }
        }
    }

    fun stop(): List<RecordedFrame> {
        running.set(false)
        worker?.join(JOIN_TIMEOUT_MS)
        failure?.let { throw IllegalStateException("frame capture failed", it) }
        return frames.toList()
    }

    private companion object {
        const val NANOS_PER_MS = 1_000_000L
        const val JOIN_TIMEOUT_MS = 30_000L
    }
}

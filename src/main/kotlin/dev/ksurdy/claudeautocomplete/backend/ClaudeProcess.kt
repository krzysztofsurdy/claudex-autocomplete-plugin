package dev.ksurdy.claudeautocomplete.backend

import java.io.BufferedWriter
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel

interface ClaudeProcess {
    val lines: ReceiveChannel<String>
    val isAlive: Boolean
    fun send(line: String)
    fun closeInput()
    fun kill()
}

fun interface ClaudeProcessFactory {
    fun start(binary: String, config: ClaudeConfig, persistent: Boolean): ClaudeProcess
}

object ProcessSupport {
    private val workDir: Path by lazy {
        Files.createTempDirectory("claude-autocomplete").also { dir ->
            Runtime.getRuntime().addShutdownHook(Thread { dir.toFile().deleteRecursively() })
        }
    }

    private val live: MutableSet<Process> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    private val shutdownHook: Unit by lazy {
        Runtime.getRuntime().addShutdownHook(Thread { live.toList().forEach(::terminate) })
    }

    fun terminate(process: Process) {
        live.remove(process)
        process.toHandle().descendants().forEach { it.destroyForcibly() }
        process.destroyForcibly()
    }

    fun launch(binary: String, config: ClaudeConfig, persistent: Boolean): Process = launch(
        ClaudeCommandBuilder.command(binary, config, persistent),
        ClaudeCommandBuilder.environment(System.getenv(), binary, config),
    )

    fun launch(command: List<String>, environment: Map<String, String>): Process {
        val builder = ProcessBuilder(command)
        builder.directory(workDir.toFile())
        builder.redirectError(ProcessBuilder.Redirect.DISCARD)
        val env = builder.environment()
        env.clear()
        env.putAll(environment)
        shutdownHook
        return builder.start().also { process ->
            live.add(process)
            process.onExit().thenRun { live.remove(process) }
        }
    }
}

class RealClaudeProcess(private val process: Process) : ClaudeProcess {
    private val writer: BufferedWriter = process.outputStream.bufferedWriter()
    private val channel = Channel<String>(Channel.UNLIMITED)

    override val lines: ReceiveChannel<String> get() = channel
    override val isAlive: Boolean get() = process.isAlive

    init {
        Thread({
            try {
                process.inputStream.bufferedReader().useLines { seq -> seq.forEach { channel.trySend(it) } }
            } catch (_: IOException) {
            } finally {
                channel.close()
            }
        }, "claude-autocomplete-reader").apply { isDaemon = true }.start()
    }

    @Synchronized
    override fun send(line: String) {
        writer.write(line)
        writer.write("\n")
        writer.flush()
    }

    override fun closeInput() {
        try {
            writer.close()
        } catch (_: IOException) {
        }
    }

    override fun kill() {
        ProcessSupport.terminate(process)
    }
}

object RealClaudeProcessFactory : ClaudeProcessFactory {
    override fun start(binary: String, config: ClaudeConfig, persistent: Boolean): ClaudeProcess =
        RealClaudeProcess(ProcessSupport.launch(binary, config, persistent))
}

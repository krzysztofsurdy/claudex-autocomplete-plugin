package dev.ksurdy.claudeautocomplete.ui

import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CaptureTripwireTest {
    @Test
    fun `sweep deletes captures under the log dir and leaves everything else`() {
        val home = Files.createTempDirectory("claudex-tripwire-")
        home.resolve("log/heartbeat").createDirectories()
        val files = listOf(
            "log/heartbeat/window.jpg", "log/screenshotBeforeKill.jpg", "log/frame.PNG", "log/idea.log", "plugins/icon.png", "config/options/ui.xml",
            "log/ui-hierarchy/static/img/show.png", "log/ui-hierarchy/ui.html",
        )
        files.forEach { home.resolve(it).also { file -> file.parent.createDirectories() }.writeText("x") }

        val swept = CaptureTripwire.sweep(home).map(Path::toString).sorted()

        assertEquals(listOf("log/frame.PNG", "log/heartbeat", "log/screenshotBeforeKill.jpg"), swept)
        assertTrue(home.resolve("log/idea.log").exists())
        assertTrue(home.resolve("plugins/icon.png").exists())
        assertTrue(home.resolve("log/ui-hierarchy/static/img/show.png").exists())
        assertTrue(!home.resolve("log/heartbeat").exists())
    }

    @Test
    fun `sweep of a missing directory finds nothing`() {
        assertTrue(CaptureTripwire.sweep(Path.of("/nonexistent/claudex-tripwire")).isEmpty())
    }
}

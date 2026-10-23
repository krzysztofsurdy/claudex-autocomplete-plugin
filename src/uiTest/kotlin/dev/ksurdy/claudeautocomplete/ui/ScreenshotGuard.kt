package dev.ksurdy.claudeautocomplete.ui

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.PluginId
import kotlin.time.Duration

@Remote("com.intellij.ide.plugins.IdeaPluginDescriptor")
interface LoadedPlugin {
    fun getPluginClassLoader(): ClassLoaderHandle
}

@Remote("com.intellij.ide.plugins.PluginManagerCore")
interface PluginLookup {
    fun getPlugin(id: PluginId): LoadedPlugin
}

@Remote("java.lang.ClassLoader")
interface ClassLoaderHandle {
    fun loadClass(name: String): ClassHandle
}

@Remote("java.lang.Class")
interface ClassHandle {
    fun getField(name: String): FieldHandle
}

@Remote("java.lang.reflect.Field")
interface FieldHandle {
    fun setAccessible(flag: Boolean)

    fun get(owner: Any?): List<CoroutineJobHandle>
}

@Remote("kotlinx.coroutines.Job")
interface CoroutineJobHandle {
    fun cancel()
}

@Remote("java.lang.System")
interface SystemProperties {
    fun getProperty(key: String): String?
}

object ScreenshotGuard {
    const val HEARTBEAT_PROPERTY = "ide.performance.screenshot"
    private const val PERFORMANCE_PLUGIN = "com.jetbrains.performancePlugin"
    private const val SERVICE = "com.jetbrains.performancePlugin.ProjectLoadedService"
    const val PROBED_FIELD = "screenshotJobs"
    private const val JOBS_FIELD = PROBED_FIELD
    private const val POLL_MS = 500L

    fun enforce(driver: Driver, wait: Duration) {
        val deadline = System.nanoTime() + wait.inWholeNanoseconds
        var state = runCatching { inspect(driver) }
        while (state.isFailure && System.nanoTime() < deadline) {
            Thread.sleep(POLL_MS)
            state = runCatching { inspect(driver) }
        }
        val seen = state.getOrElse { error("cannot inspect the IDE's periodic OS screenshot state, aborting: ${it.message}") }
        check(seen.property == null) { "$HEARTBEAT_PROPERTY is set to '${seen.property}' in the IDE, aborting" }
        check(seen.cancelledJobs == 0) { "cancelled ${seen.cancelledJobs} periodic OS screenshot job(s) that should never exist, aborting" }
    }

    private fun inspect(driver: Driver): Inspection {
        val property = driver.utility(SystemProperties::class).getProperty(HEARTBEAT_PROPERTY)
        val id = driver.utility(PluginId::class).getId(PERFORMANCE_PLUGIN)
        val loader = driver.utility(PluginLookup::class).getPlugin(id).getPluginClassLoader()
        val field = loader.loadClass(SERVICE).getField(JOBS_FIELD)
        field.setAccessible(true)
        val jobs = field.get(null)
        jobs.forEach { it.cancel() }
        return Inspection(property, jobs.size)
    }

    private class Inspection(val property: String?, val cancelledJobs: Int)
}

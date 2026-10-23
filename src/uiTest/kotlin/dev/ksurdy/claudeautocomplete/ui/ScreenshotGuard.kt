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

object ScreenshotGuard {
    private const val PERFORMANCE_PLUGIN = "com.jetbrains.performancePlugin"
    private const val SERVICE = "com.jetbrains.performancePlugin.ProjectLoadedService"
    private const val JOBS_FIELD = "screenshotJobs"
    private const val POLL_MS = 500L

    fun cancelPeriodicScreenshots(driver: Driver, wait: Duration): Int {
        val deadline = System.nanoTime() + wait.inWholeNanoseconds
        while (true) {
            val cancelled = runCatching { cancelOnce(driver) }.getOrDefault(0)
            if (cancelled > 0 || System.nanoTime() >= deadline) return cancelled
            Thread.sleep(POLL_MS)
        }
    }

    private fun cancelOnce(driver: Driver): Int {
        val id = driver.utility(PluginId::class).getId(PERFORMANCE_PLUGIN)
        val loader = driver.utility(PluginLookup::class).getPlugin(id).getPluginClassLoader()
        val field = loader.loadClass(SERVICE).getField(JOBS_FIELD)
        field.setAccessible(true)
        val jobs = field.get(null)
        jobs.forEach { it.cancel() }
        return jobs.size
    }
}

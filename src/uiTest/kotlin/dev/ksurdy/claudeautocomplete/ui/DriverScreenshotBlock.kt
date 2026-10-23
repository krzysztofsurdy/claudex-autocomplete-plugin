package dev.ksurdy.claudeautocomplete.ui

import com.intellij.driver.client.Driver
import com.intellij.driver.client.impl.JmxCallException
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicInteger

object DriverScreenshotBlock {
    private const val WRAPPER = "com.intellij.ide.starter.driver.engine.DriverWithDetailedLogging"
    private const val DELEGATE_FIELD = "driver"

    fun install(starterDriver: Driver, blocked: AtomicInteger) {
        check(starterDriver::class.java.name == WRAPPER) {
            "unexpected Driver ${starterDriver::class.java.name}, cannot block Driver.takeScreenshot, aborting"
        }
        val field = starterDriver::class.java.getDeclaredField(DELEGATE_FIELD)
        field.isAccessible = true
        field.set(starterDriver, blockingProxy(field.get(starterDriver) as Driver, blocked))
    }

    private fun blockingProxy(inner: Driver, blocked: AtomicInteger): Driver =
        Proxy.newProxyInstance(Driver::class.java.classLoader, arrayOf(Driver::class.java)) { _, method, args ->
            if (method.name == "takeScreenshot") {
                blocked.incrementAndGet()
                throw JmxCallException("Driver.takeScreenshot captures the whole OS screen and is blocked in UI tests", null)
            }
            try {
                method.invoke(inner, *(args ?: emptyArray()))
            } catch (failure: InvocationTargetException) {
                throw failure.targetException
            }
        } as Driver
}

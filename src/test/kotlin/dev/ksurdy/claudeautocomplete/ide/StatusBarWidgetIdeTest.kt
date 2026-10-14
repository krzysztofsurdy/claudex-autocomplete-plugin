package dev.ksurdy.claudeautocomplete.ide

import com.intellij.codeInsight.inline.completion.testInlineCompletion
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.ksurdy.claudeautocomplete.BackendService
import dev.ksurdy.claudeautocomplete.ClaudeAutocompleteSettings
import dev.ksurdy.claudeautocomplete.ClaudeStatus
import dev.ksurdy.claudeautocomplete.ClaudexIcons
import dev.ksurdy.claudeautocomplete.CompletionEngine
import dev.ksurdy.claudeautocomplete.StatusService
import dev.ksurdy.claudeautocomplete.backend.CompletionResult
import dev.ksurdy.claudeautocomplete.backend.UsageLimits
import dev.ksurdy.claudeautocomplete.backend.UsageWindow
import dev.ksurdy.claudeautocomplete.ui.ClaudeStatusBarWidget
import kotlinx.coroutines.CompletableDeferred
import java.awt.image.BufferedImage
import java.lang.reflect.Proxy
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.Icon
import kotlin.time.Duration.Companion.seconds

class StatusBarWidgetIdeTest : BasePlatformTestCase() {
    private lateinit var fake: FakeCompletionBackend
    private lateinit var original: CompletionEngine
    private lateinit var widget: ClaudeStatusBarWidget
    private val updates = AtomicInteger()

    override fun runInDispatchThread(): Boolean = false

    override fun setUp() {
        super.setUp()
        val service = BackendService.getInstance()
        original = service.engine
        fake = FakeCompletionBackend(CompletionResult.Success("world"))
        service.engine = fake
        ClaudeAutocompleteSettings.getInstance().state.debounceMs = 0
        ClaudeAutocompleteSettings.getInstance().state.consentGiven = true
        StatusService.getInstance().update(ClaudeStatus.Ready)
        widget = ClaudeStatusBarWidget(project)
        onEdt { widget.install(statusBar()) }
    }

    override fun tearDown() {
        try {
            onEdt { widget.dispose() }
            BackendService.getInstance().engine = original
            StatusService.getInstance().blockUntil(null)
            StatusService.getInstance().clearUsage()
            StatusService.getInstance().update(ClaudeStatus.Ready)
            ClaudeAutocompleteSettings.getInstance().loadState(ClaudeAutocompleteSettings.State())
        } finally {
            super.tearDown()
        }
    }

    private fun statusBar(): StatusBar = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(StatusBar::class.java)) { _, method, _ ->
        if (method.name == "updateWidget") updates.incrementAndGet()
        null
    } as StatusBar

    private fun <T> onEdt(block: () -> T): T {
        var result: Result<T>? = null
        ApplicationManager.getApplication().invokeAndWait { result = runCatching(block) }
        return result!!.getOrThrow()
    }

    private fun presentation() = widget.getPresentation() as StatusBarWidget.IconPresentation

    private fun icon(): Icon? = onEdt { presentation().getIcon() }

    private fun settle() = onEdt { }

    private fun report(status: ClaudeStatus) {
        StatusService.getInstance().update(status)
        settle()
    }

    private fun awaitStatus(check: (ClaudeStatus) -> Boolean) {
        repeat(200) {
            if (check(StatusService.getInstance().status)) return
            Thread.sleep(25)
        }
        fail("status never matched, was ${StatusService.getInstance().status}")
    }

    private fun saturation(icon: Icon): Int {
        val image = BufferedImage(icon.iconWidth.coerceAtLeast(16), icon.iconHeight.coerceAtLeast(16), BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        icon.paintIcon(null, graphics, 0, 0)
        graphics.dispose()
        var widest = 0
        for (x in 0 until image.width) for (y in 0 until image.height) {
            val argb = image.getRGB(x, y)
            if (argb ushr 24 < 0x80) continue
            val channels = intArrayOf(argb shr 16 and 0xFF, argb shr 8 and 0xFF, argb and 0xFF)
            widest = maxOf(widest, channels.max() - channels.min())
        }
        return widest
    }

    fun testPresentationIsIconOnly() {
        assertTrue(widget.getPresentation() is StatusBarWidget.IconPresentation)
        assertFalse(widget.getPresentation() is StatusBarWidget.TextPresentation)
        assertNotNull(presentation().getClickConsumer())
    }

    fun testGreyIconIsGreyAndLogoIsColoured() {
        assertTrue(saturation(ClaudexIcons.Logo).toString(), saturation(ClaudexIcons.Logo) > 60)
        assertTrue(saturation(ClaudexIcons.Grey).toString(), saturation(ClaudexIcons.Grey) < 16)
    }

    fun testColouredWhenReadyWaitingOrDone() {
        assertSame(ClaudexIcons.Logo, icon())
        report(ClaudeStatus.Waiting(System.currentTimeMillis()))
        assertSame(ClaudexIcons.Logo, icon())
        report(ClaudeStatus.Done(700, System.currentTimeMillis()))
        assertSame(ClaudexIcons.Logo, icon())
        assertFalse(widget.isBlinking)
    }

    fun testGreyWhenThePluginIsDisabled() {
        ClaudeAutocompleteSettings.getInstance().state.enabled = false
        StatusService.getInstance().refresh()
        settle()
        assertSame(ClaudexIcons.Grey, icon())
        assertFalse(widget.isBlinking)
    }

    fun testGreyWithoutConsent() {
        ClaudeAutocompleteSettings.getInstance().state.consentGiven = false
        StatusService.getInstance().refresh()
        settle()
        assertSame(ClaudexIcons.Grey, icon())
    }

    fun testGreyAfterAFailureAndAtTheLimit() {
        report(ClaudeStatus.Error("not logged in"))
        assertSame(ClaudexIcons.Grey, icon())
        report(ClaudeStatus.Ready)
        assertSame(ClaudexIcons.Logo, icon())
        report(ClaudeStatus.LimitReached(Instant.now().plusSeconds(60)))
        assertSame(ClaudexIcons.Grey, icon())
        assertFalse(widget.isBlinking)
    }

    fun testBlinkingRunsOnlyWhileThinking() {
        assertFalse(widget.isBlinking)
        report(ClaudeStatus.Thinking(System.currentTimeMillis()))
        assertTrue(widget.isBlinking)
        report(ClaudeStatus.Done(5, System.currentTimeMillis()))
        assertFalse(widget.isBlinking)
        report(ClaudeStatus.Thinking(System.currentTimeMillis()))
        assertTrue(widget.isBlinking)
        report(ClaudeStatus.Error("boom"))
        assertFalse(widget.isBlinking)
        assertSame(ClaudexIcons.Grey, icon())
    }

    fun testThinkingAlternatesBetweenColouredAndGrey() {
        report(ClaudeStatus.Thinking(System.currentTimeMillis()))
        val seen = ArrayList<Icon?>()
        val deadline = System.currentTimeMillis() + 2_600
        while (System.currentTimeMillis() < deadline) {
            val now = icon()
            if (seen.lastOrNull() !== now) seen += now
            Thread.sleep(40)
        }
        assertTrue(seen.toString(), seen.size >= 4)
        assertTrue(seen.toString(), seen.all { it === ClaudexIcons.Logo || it === ClaudexIcons.Grey })
        assertTrue(seen.toString(), seen.zipWithNext().all { (a, b) -> a !== b })
        assertTrue(updates.get() >= 4)
    }

    fun testDisposeStopsTheTimerAndTheUpdates() {
        report(ClaudeStatus.Thinking(System.currentTimeMillis()))
        assertTrue(widget.isBlinking)
        onEdt { widget.dispose() }
        assertFalse(widget.isBlinking)
        val before = updates.get()
        StatusService.getInstance().update(ClaudeStatus.Thinking(System.currentTimeMillis() + 1))
        settle()
        Thread.sleep(1_200)
        assertEquals(before, updates.get())
        assertFalse(widget.isBlinking)
    }

    fun testBlinksWhileARequestIsInFlightAndStopsAfterIt() = myFixture.testInlineCompletion(timeout = 30.seconds) {
        val gate = CompletableDeferred<Unit>()
        fake.gate = gate
        init(PlainTextFileType.INSTANCE, "blink1 <caret>")
        callInlineCompletion()
        awaitStatus { it is ClaudeStatus.Thinking }
        settle()
        assertTrue(widget.isBlinking)
        val seen = HashSet<Icon?>()
        repeat(30) {
            seen += icon()
            delay(50)
        }
        assertTrue(seen.toString(), seen.contains(ClaudexIcons.Logo) && seen.contains(ClaudexIcons.Grey))
        gate.complete(Unit)
        delay()
        settle()
        assertTrue(StatusService.getInstance().status.toString(), StatusService.getInstance().status is ClaudeStatus.Done)
        assertFalse(widget.isBlinking)
        assertSame(ClaudexIcons.Logo, icon())
        val settled = updates.get()
        delay(1_200)
        assertEquals(settled, updates.get())
        assertSame(ClaudexIcons.Logo, icon())
    }

    fun testTooltipCarriesProviderStateLatencyModelAndUsage() {
        val state = ClaudeAutocompleteSettings.getInstance().state
        state.model = "haiku"
        StatusService.getInstance().updateUsage(
            UsageLimits(UsageWindow(0.42, Instant.now().plusSeconds(3_600)), UsageWindow(0.18, null), "allowed", Instant.now()),
        )
        report(ClaudeStatus.Done(700, System.currentTimeMillis()))
        val tip = onEdt { presentation().getTooltipText() }.orEmpty()
        assertTrue(tip, tip.contains("Provider: Claude"))
        assertTrue(tip, tip.contains("State: "))
        assertTrue(tip, tip.contains("Last request: 0.7s"))
        assertTrue(tip, tip.contains("Model: haiku"))
        assertTrue(tip, tip.contains("5h: 42%"))
        assertTrue(tip, tip.contains("7d: 18%"))
    }
}

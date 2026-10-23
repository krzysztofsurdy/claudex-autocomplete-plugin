package dev.ksurdy.claudeautocomplete.ui

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.model.LockSemantics
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.ui.remote.Component
import java.nio.file.Path

@Remote("java.awt.Graphics")
interface AwtGraphics {
    fun dispose()
}

@Remote("java.awt.image.BufferedImage")
interface OffscreenImage {
    fun createGraphics(): AwtGraphics
}

@Remote("java.awt.Component")
interface PaintableComponent {
    fun paint(graphics: AwtGraphics)

    fun getWidth(): Int

    fun getHeight(): Int
}

@Remote("javax.swing.RootPaneContainer")
interface RootPaneHolder {
    fun getRootPane(): PaintableComponent
}

@Remote("java.io.File")
interface OutputFile

@Remote("javax.imageio.ImageIO")
interface ImageWriters {
    fun write(image: OffscreenImage, format: String, output: OutputFile): Boolean
}

@Remote("dev.ksurdy.claudeautocomplete.ClaudeAutocompleteConfigurable", plugin = "dev.ksurdy.claudeautocomplete")
interface SettingsPage {
    fun createComponent(): PaintableComponent

    fun reset()
}

@Remote("javax.swing.BorderFactory")
interface Borders {
    fun createEmptyBorder(top: Int, left: Int, bottom: Int, right: Int): BorderHandle
}

@Remote("javax.swing.border.Border")
interface BorderHandle

@Remote("javax.swing.JComponent")
interface ContentPane {
    fun add(component: PaintableComponent): PaintableComponent

    fun setBorder(border: BorderHandle)
}

@Remote("javax.swing.JFrame")
interface HostFrame {
    fun getContentPane(): ContentPane

    fun getRootPane(): PaintableComponent

    fun setSize(width: Int, height: Int)

    fun addNotify()

    fun validate()

    fun dispose()
}

class IdeCanvas(private val driver: Driver, private val frame: Component) {
    fun paint(target: Path, format: String = FAST_FORMAT) {
        driver.withContext(OnDispatcher.EDT, LockSemantics.READ_ACTION) {
            render(this, cast(frame, RootPaneHolder::class).getRootPane(), target, format)
        }
    }

    fun paintSettingsPage(target: Path, width: Int, height: Int) {
        driver.withContext(OnDispatcher.EDT, LockSemantics.READ_ACTION) {
            val page = new(SettingsPage::class)
            val host = new(HostFrame::class)
            try {
                val content = host.getContentPane()
                content.setBorder(utility(Borders::class).createEmptyBorder(PAGE_PADDING, PAGE_PADDING, PAGE_PADDING, PAGE_PADDING))
                content.add(page.createComponent())
                host.setSize(width, height + WINDOW_INSETS)
                host.addNotify()
                host.validate()
                page.reset()
                host.validate()
                render(this, host.getRootPane(), target, "png")
            } finally {
                host.dispose()
            }
        }
    }

    private fun render(remote: Driver, root: PaintableComponent, target: Path, format: String) {
        val image = remote.new(OffscreenImage::class, root.getWidth(), root.getHeight(), TYPE_INT_RGB)
        val graphics = image.createGraphics()
        root.paint(graphics)
        graphics.dispose()
        val written = remote.utility(ImageWriters::class).write(image, format, remote.new(OutputFile::class, target.toString()))
        check(written) { "no $format writer for $target" }
    }

    companion object {
        const val FAST_FORMAT = "bmp"
        private const val TYPE_INT_RGB = 1
        private const val PAGE_PADDING = 24
        private const val WINDOW_INSETS = 28
    }
}

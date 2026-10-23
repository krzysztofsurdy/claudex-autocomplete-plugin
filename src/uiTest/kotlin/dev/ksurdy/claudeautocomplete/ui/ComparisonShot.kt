package dev.ksurdy.claudeautocomplete.ui

import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.nio.file.Path
import javax.imageio.ImageIO

object ComparisonShot {
    private const val WIDTH = MediaStage.FRAME_WIDTH
    private const val HEIGHT = MediaStage.FRAME_HEIGHT
    private const val GAP = 8
    private const val PANEL_HEIGHT = 396
    private const val CROP_TOP = 302
    private const val LABEL_RIGHT = 1210
    private const val LABEL_TOP = 12
    private const val LABEL_HEIGHT = 28
    private const val LABEL_PADDING = 14
    private val BACKGROUND = Color(0x2B2D30)
    private val LABEL_FILL = Color(0x3574F0)
    private val LABEL_TEXT = Color.WHITE
    private val FONT = Font(Font.SANS_SERIF, Font.BOLD, 15)

    fun compose(first: Path, second: Path, target: Path) {
        val canvas = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB)
        val graphics = canvas.createGraphics()
        graphics.color = BACKGROUND
        graphics.fillRect(0, 0, WIDTH, HEIGHT)
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        graphics.font = FONT
        panel(graphics, first, 0, "Suggestion")
        panel(graphics, second, PANEL_HEIGHT + GAP, "Alt+]  another suggestion")
        graphics.dispose()
        check(ImageIO.write(canvas, "png", target.toFile())) { "no png writer for $target" }
    }

    private fun panel(graphics: java.awt.Graphics2D, source: Path, top: Int, label: String) {
        val shot = ImageIO.read(source.toFile())
        graphics.drawImage(shot.getSubimage(0, CROP_TOP, WIDTH, PANEL_HEIGHT), 0, top, null)
        val textWidth = graphics.fontMetrics.stringWidth(label)
        val width = textWidth + LABEL_PADDING * 2
        val left = LABEL_RIGHT - width
        graphics.color = LABEL_FILL
        graphics.fillRoundRect(left, top + LABEL_TOP, width, LABEL_HEIGHT, LABEL_HEIGHT, LABEL_HEIGHT)
        graphics.color = LABEL_TEXT
        graphics.drawString(label, left + LABEL_PADDING, top + LABEL_TOP + LABEL_HEIGHT / 2 + graphics.fontMetrics.ascent / 2 - 2)
    }
}

package dev.ksurdy.claudeautocomplete.ui

import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.awt.image.IndexColorModel
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageTypeSpecifier
import javax.imageio.ImageWriter
import javax.imageio.metadata.IIOMetadata
import javax.imageio.metadata.IIOMetadataNode

class GifFrame(val file: Path, val delayMs: Long)

object GifWriter {
    private const val FORMAT = "javax_imageio_gif_image_1.0"
    private const val PALETTE_SAMPLE_STEP = 3
    private const val MIN_DELAY_CS = 2L
    private const val RGB_MASK = 0xFFFFFF

    fun write(frames: List<GifFrame>, target: Path) {
        require(frames.isNotEmpty()) { "no frames to write" }
        val palette = paletteFor(frames)
        val writer = ImageIO.getImageWritersByFormatName("gif").next()
        Files.createDirectories(target.parent)
        ImageIO.createImageOutputStream(target.toFile()).use { output ->
            writer.output = output
            writer.prepareWriteSequence(null)
            var previous: ByteArray? = null
            frames.forEachIndexed { index, frame ->
                val image = ImageIO.read(frame.file.toFile())
                val indexed = indices(image, palette)
                val whole = Rectangle(0, 0, image.width, image.height)
                val area = previous?.let { changedArea(it, indexed, image.width, image.height) } ?: whole
                previous = indexed
                val piece = crop(indexed, image.width, area, palette)
                val metadata = metadataFor(writer, piece, area, frame.delayMs, index == 0)
                writer.writeToSequence(IIOImage(piece, null, metadata), null)
            }
            writer.endWriteSequence()
        }
        writer.dispose()
    }

    fun pixels(image: BufferedImage): IntArray = image.getRGB(0, 0, image.width, image.height, null, 0, image.width)

    private fun paletteFor(frames: List<GifFrame>): IndexColorModel {
        val histogram = ColorHistogram()
        val sampled = frames.filterIndexed { index, _ -> index % PALETTE_SAMPLE_STEP == 0 || index == frames.lastIndex }
        for (frame in sampled) histogram.add(pixels(ImageIO.read(frame.file.toFile())))
        return histogram.palette()
    }

    private fun indices(image: BufferedImage, palette: IndexColorModel): ByteArray {
        val cache = HashMap<Int, Byte>()
        val source = pixels(image)
        return ByteArray(source.size) { cache.getOrPut(source[it] and RGB_MASK) { nearest(source[it], palette) } }
    }

    private fun nearest(rgb: Int, palette: IndexColorModel): Byte {
        var best = 0
        var bestDistance = Int.MAX_VALUE
        for (index in 0 until palette.mapSize) {
            val dr = ((rgb shr 16) and 0xFF) - palette.getRed(index)
            val dg = ((rgb shr 8) and 0xFF) - palette.getGreen(index)
            val db = (rgb and 0xFF) - palette.getBlue(index)
            val distance = 3 * dr * dr + 4 * dg * dg + 2 * db * db
            if (distance < bestDistance) {
                bestDistance = distance
                best = index
            }
        }
        return best.toByte()
    }

    private fun changedArea(before: ByteArray, after: ByteArray, width: Int, height: Int): Rectangle {
        var minX = width
        var minY = height
        var maxX = -1
        var maxY = -1
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (before[y * width + x] == after[y * width + x]) continue
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
        }
        return if (maxX < 0) Rectangle(0, 0, 1, 1) else Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1)
    }

    private fun crop(indexed: ByteArray, width: Int, area: Rectangle, palette: IndexColorModel): BufferedImage {
        val piece = BufferedImage(area.width, area.height, BufferedImage.TYPE_BYTE_INDEXED, palette)
        for (y in 0 until area.height) {
            val from = (area.y + y) * width + area.x
            piece.raster.setDataElements(0, y, area.width, 1, indexed.copyOfRange(from, from + area.width))
        }
        return piece
    }

    private fun metadataFor(writer: ImageWriter, piece: BufferedImage, area: Rectangle, delayMs: Long, first: Boolean): IIOMetadata {
        val metadata = writer.getDefaultImageMetadata(ImageTypeSpecifier(piece), null)
        val root = metadata.getAsTree(FORMAT) as IIOMetadataNode
        child(root, "ImageDescriptor").apply {
            setAttribute("imageLeftPosition", area.x.toString())
            setAttribute("imageTopPosition", area.y.toString())
            setAttribute("imageWidth", area.width.toString())
            setAttribute("imageHeight", area.height.toString())
        }
        child(root, "GraphicControlExtension").apply {
            setAttribute("disposalMethod", "none")
            setAttribute("userInputFlag", "FALSE")
            setAttribute("transparentColorFlag", "FALSE")
            setAttribute("transparentColorIndex", "0")
            setAttribute("delayTime", (delayMs / 10).coerceAtLeast(MIN_DELAY_CS).toString())
        }
        if (first) {
            val extension = IIOMetadataNode("ApplicationExtension").apply {
                setAttribute("applicationID", "NETSCAPE")
                setAttribute("authenticationCode", "2.0")
                userObject = byteArrayOf(1, 0, 0)
            }
            child(root, "ApplicationExtensions").appendChild(extension)
        }
        metadata.setFromTree(FORMAT, root)
        return metadata
    }

    private fun child(root: IIOMetadataNode, name: String): IIOMetadataNode {
        val existing = root.getElementsByTagName(name)
        if (existing.length > 0) return existing.item(0) as IIOMetadataNode
        return IIOMetadataNode(name).also { root.appendChild(it) }
    }
}

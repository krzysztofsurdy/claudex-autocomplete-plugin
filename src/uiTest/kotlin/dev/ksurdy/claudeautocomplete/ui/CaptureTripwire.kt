package dev.ksurdy.claudeautocomplete.ui

import com.intellij.ide.starter.path.GlobalPaths
import java.io.ByteArrayOutputStream
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.jar.Attributes
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.writeBytes

@OptIn(ExperimentalPathApi::class)
object CaptureTripwire {
    private val NOT_SCANNED = setOf("plugins", "config", "system")
    private const val VIEWER = "ui-hierarchy"
    private const val VIEWER_ASSETS = "static"
    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png")

    fun disarmKillScreenshot() {
        val jar = GlobalPaths.instance.getCacheDirectoryFor("tools").resolve("TakeScreenshot").resolve("TakeScreenshot.jar")
        jar.createParentDirectories()
        jar.writeBytes(inertJar())
    }

    fun sweep(root: Path): List<Path> {
        if (!root.isDirectory()) return emptyList()
        val hits = mutableListOf<Path>()
        Files.walkFileTree(
            root,
            object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult = when {
                    dir.parent == root && dir.name in NOT_SCANNED -> FileVisitResult.SKIP_SUBTREE
                    dir.name == VIEWER_ASSETS && dir.parent?.name == VIEWER -> FileVisitResult.SKIP_SUBTREE
                    dir != root && isCaptureName(dir) -> hits.add(dir).let { FileVisitResult.SKIP_SUBTREE }
                    else -> FileVisitResult.CONTINUE
                }

                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (isCaptureName(file) || file.extension.lowercase() in IMAGE_EXTENSIONS) hits.add(file)
                    return FileVisitResult.CONTINUE
                }
            },
        )
        hits.forEach { it.deleteRecursively() }
        return hits.map(root::relativize)
    }

    private fun isCaptureName(path: Path): Boolean {
        val name = path.name.lowercase()
        return "screenshot" in name || name.startsWith("heartbeat")
    }

    private fun inertJar(): ByteArray {
        val manifest = Manifest().apply { mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0" }
        val bytes = ByteArrayOutputStream()
        JarOutputStream(bytes, manifest).close()
        return bytes.toByteArray()
    }
}

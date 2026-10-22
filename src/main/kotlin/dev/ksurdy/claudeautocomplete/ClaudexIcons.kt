package dev.ksurdy.claudeautocomplete

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

object ClaudexIcons {
    @JvmField
    val Logo: Icon = IconLoader.getIcon("/icons/claudex.svg", ClaudexIcons::class.java)

    @JvmField
    val Grey: Icon = IconLoader.getDisabledIcon(Logo)
}

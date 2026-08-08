package dev.ksurdy.claudeautocomplete.backend

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ClaudeCliLocatorTest {
    private fun locator(existing: Set<String>, shell: String? = null) =
        ClaudeCliLocator(home = "/home/u", isExecutable = { it in existing }, shellLookup = { shell })

    @Test
    fun `configured path wins when executable`() {
        assertEquals("/x/claude", locator(setOf("/x/claude", "/home/u/.local/bin/claude")).resolve("/x/claude"))
    }

    @Test
    fun `configured path that does not exist yields null`() {
        assertNull(locator(setOf("/home/u/.local/bin/claude")).resolve("/missing/claude"))
    }

    @Test
    fun `auto detect follows candidate order`() {
        val found = locator(setOf("/opt/homebrew/bin/claude", "/usr/local/bin/claude")).resolve("")
        assertEquals("/opt/homebrew/bin/claude", found)
    }

    @Test
    fun `falls back to login shell lookup`() {
        assertEquals("/weird/claude", locator(setOf("/weird/claude"), shell = "/weird/claude").resolve(""))
    }

    @Test
    fun `nothing found yields null`() {
        assertNull(locator(emptySet()).resolve(" "))
    }
}

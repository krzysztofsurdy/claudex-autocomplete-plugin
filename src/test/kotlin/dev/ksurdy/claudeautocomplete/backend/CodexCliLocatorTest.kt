package dev.ksurdy.claudeautocomplete.backend

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CodexCliLocatorTest {
    private fun locate(configured: String, existing: Set<String>, nvm: List<String> = emptyList(), shell: String? = null) =
        CodexCliLocator.locate(configured, "/home/u", { it in existing }, { nvm }, { shell })

    @Test
    fun `configured path wins`() = assertEquals("/x/codex", locate("/x/codex", setOf("/x/codex", "/opt/homebrew/bin/codex")))

    @Test
    fun `configured missing yields null`() = assertNull(locate("/missing", setOf("/opt/homebrew/bin/codex")))

    @Test
    fun `auto detect order`() =
        assertEquals("/home/u/.local/bin/codex", locate("", setOf("/opt/homebrew/bin/codex", "/home/u/.local/bin/codex")))

    @Test
    fun `npm global and nvm`() {
        assertEquals("/home/u/.npm-global/bin/codex", locate("", setOf("/home/u/.npm-global/bin/codex")))
        assertEquals("/home/u/.nvm/versions/node/v22/bin/codex", locate("", setOf("/home/u/.nvm/versions/node/v22/bin/codex"), nvm = listOf("/home/u/.nvm/versions/node/v22/bin/codex")))
    }

    @Test
    fun `login shell fallback`() = assertEquals("/w/codex", locate("", setOf("/w/codex"), shell = "/w/codex"))

    @Test
    fun `nothing found`() = assertNull(locate("", emptySet()))
}

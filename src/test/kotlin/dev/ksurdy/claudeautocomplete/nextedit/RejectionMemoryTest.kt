package dev.ksurdy.claudeautocomplete.nextedit

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RejectionMemoryTest {
    private var now = 0L
    private val memory = RejectionMemory({ now })

    @Test
    fun `remembers a rejected suggestion`() {
        memory.remember("a.php", 3, 3, "    return \$ab;")
        assertTrue(memory.contains("a.php", 3, 3, "    return \$ab;"))
    }

    @Test
    fun `an unknown suggestion is not remembered`() {
        assertFalse(memory.contains("a.php", 3, 3, "x"))
    }

    @Test
    fun `whitespace differences do not make a suggestion new`() {
        memory.remember("a.php", 3, 3, "    return   \$ab;  ")
        assertTrue(memory.contains("a.php", 3, 3, "return \$ab;"))
        assertTrue(memory.contains("a.php", 3, 3, "\treturn\n\$ab;"))
    }

    @Test
    fun `another range, text or file is a different suggestion`() {
        memory.remember("a.php", 3, 3, "x")
        assertFalse(memory.contains("a.php", 4, 4, "x"))
        assertFalse(memory.contains("a.php", 3, 4, "x"))
        assertFalse(memory.contains("a.php", 3, 3, "y"))
        assertFalse(memory.contains("b.php", 3, 3, "x"))
    }

    @Test
    fun `forgets after five minutes`() {
        memory.remember("a.php", 3, 3, "x")
        now = 299_999
        assertTrue(memory.contains("a.php", 3, 3, "x"))
        now = 300_000
        assertFalse(memory.contains("a.php", 3, 3, "x"))
    }

    @Test
    fun `remembering again restarts the five minutes`() {
        memory.remember("a.php", 3, 3, "x")
        now = 200_000
        memory.remember("a.php", 3, 3, "x")
        now = 400_000
        assertTrue(memory.contains("a.php", 3, 3, "x"))
    }

    @Test
    fun `keeps a bounded number of entries and drops the oldest`() {
        val small = RejectionMemory({ now }, maxEntries = 2)
        small.remember("a", 1, 1, "one")
        small.remember("a", 2, 2, "two")
        small.remember("a", 3, 3, "three")
        assertFalse(small.contains("a", 1, 1, "one"))
        assertTrue(small.contains("a", 2, 2, "two"))
        assertTrue(small.contains("a", 3, 3, "three"))
    }
}

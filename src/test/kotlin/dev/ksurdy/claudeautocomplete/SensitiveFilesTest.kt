package dev.ksurdy.claudeautocomplete

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SensitiveFilesTest {
    private val defaults = SensitiveFiles.DEFAULT_PATTERNS

    @Test
    fun blocksDotEnvVariants() {
        listOf(".env", ".env.local", ".env.production", ".ENV").forEach { assertTrue(SensitiveFiles.isSensitive(it, defaults), it) }
    }

    @Test
    fun allowsEnvTemplates() {
        listOf(".env.example", ".env.dist").forEach { assertFalse(SensitiveFiles.isSensitive(it, defaults), it) }
    }

    @Test
    fun blocksKeyAndCredentialFiles() {
        listOf(
            "server.pem", "tls.key", "store.p12", "cert.pfx", "id_rsa", "id_rsa.pub", "id_ed25519", "app.keystore", "release.jks",
            ".npmrc", ".pypirc", ".netrc", "auth.json", "credentials", "credentials.json", "secrets.yaml", "vault.kdbx",
        ).forEach { assertTrue(SensitiveFiles.isSensitive(it, defaults), it) }
    }

    @Test
    fun allowsOrdinarySources() {
        listOf("Main.kt", "README.md", "environment.ts", "keyboard.js", "secrets_helper.php", "composer.json").forEach {
            assertFalse(SensitiveFiles.isSensitive(it, defaults), it)
        }
    }

    @Test
    fun usesCustomPatterns() {
        assertTrue(SensitiveFiles.isSensitive("private.txt", "private.*"))
        assertFalse(SensitiveFiles.isSensitive(".env", "private.*"))
    }

    @Test
    fun emptyPatternsBlockNothing() {
        assertFalse(SensitiveFiles.isSensitive(".env", ""))
    }

    @Test
    fun parsesCommaSeparatedPatternsIgnoringBlanks() {
        assertEquals(listOf("*.pem", ".env"), SensitiveFiles.parse(" *.pem , ,.env "))
    }

    @Test
    fun treatsRegexCharactersLiterally() {
        assertTrue(SensitiveFiles.isSensitive("a+b.txt", "a+b.txt"))
        assertFalse(SensitiveFiles.isSensitive("aab.txt", "a+b.txt"))
    }

    @Test
    fun questionMarkMatchesSingleCharacter() {
        assertTrue(SensitiveFiles.isSensitive("k1.pem", "k?.pem"))
        assertFalse(SensitiveFiles.isSensitive("k12.pem", "k?.pem"))
    }
}

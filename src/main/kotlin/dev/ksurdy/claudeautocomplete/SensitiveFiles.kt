package dev.ksurdy.claudeautocomplete

object SensitiveFiles {
    const val DEFAULT_PATTERNS =
        ".env, .env.*, *.pem, *.key, *.p12, *.pfx, id_rsa*, id_ed25519*, *.keystore, *.jks, " +
            ".npmrc, .pypirc, .netrc, auth.json, credentials*, secrets.*, *.kdbx"

    private val allowedNames = setOf(".env.example", ".env.dist")

    fun parse(patterns: String): List<String> = patterns.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    fun isSensitive(fileName: String, patterns: String): Boolean {
        if (fileName.lowercase() in allowedNames) return false
        return parse(patterns).any { globToRegex(it).matches(fileName) }
    }

    private fun globToRegex(glob: String): Regex {
        val body = buildString {
            glob.forEach { ch ->
                when (ch) {
                    '*' -> append(".*")
                    '?' -> append('.')
                    else -> append(Regex.escape(ch.toString()))
                }
            }
        }
        return Regex(body, RegexOption.IGNORE_CASE)
    }
}

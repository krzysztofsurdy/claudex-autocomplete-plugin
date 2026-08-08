package dev.ksurdy.claudeautocomplete.backend

fun testConfig(
    claudePath: String = "/usr/local/bin/claude",
    model: String = "haiku",
    fallbackModel: String = "",
    effort: String = "low",
    thinkingEnabled: Boolean = false,
    thinkingBudgetTokens: Int = 1024,
    requestTimeoutMs: Int = 8000,
    persistentProcess: Boolean = true,
    customInstructions: String = "",
) = ClaudeConfig(
    claudePath, model, fallbackModel, effort, thinkingEnabled, thinkingBudgetTokens,
    requestTimeoutMs, persistentProcess, customInstructions,
)

fun testContext(
    prefix: String = "<?php\nfunction add(",
    suffix: String = "",
    multiline: Boolean = false,
    maxLines: Int = 12,
    openFiles: List<OpenFileSnippet> = emptyList(),
    filePath: String = "src/Foo.php",
    languageId: String = "PHP",
) = CompletionContext(filePath, languageId, prefix, suffix, openFiles, multiline, maxLines)

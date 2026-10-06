package dev.ksurdy.claudeautocomplete

object OptionLabels {
    private val labels = mapOf(
        "none" to "None",
        "minimal" to "Minimal",
        "low" to "Low",
        "medium" to "Medium",
        "high" to "High",
        "xhigh" to "Extra high",
        "max" to "Max",
        "auto" to "Auto",
        "always" to "Always",
        "never" to "Never",
    )

    fun display(value: String): String = labels[value] ?: value
}

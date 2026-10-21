package dev.ksurdy.claudeautocomplete.context

object StopWords {
    private val GENERIC = words(
        "this self null NULL nil none None true True TRUE false False FALSE the and not for new var int string bool void return if else",
    )

    private val C_STYLE = GENERIC + words(
        """
        class function fun func fn def public private protected static final abstract interface extends implements
        import package namespace use while switch case break continue try catch finally throw throws const let val
        readonly override object async await yield enum struct trait impl echo array float double long char byte
        boolean typeof instanceof default export require include as is do internal sealed
        """,
    )

    private val SCRIPTING = GENERIC + words(
        """
        def class import from as with pass lambda elif elsif except raise global nonlocal assert del yield async
        await end do begin rescue ensure module require include unless until then puts print or in is while try
        """,
    )

    private val SCRIPTING_LANGUAGES = setOf("python", "ruby", "shell script", "bash", "sh")

    fun forLanguage(languageId: String): Set<String> =
        if (languageId.lowercase() in SCRIPTING_LANGUAGES) SCRIPTING else C_STYLE

    private fun words(text: String): Set<String> = text.split(Regex("\\s+")).filter { it.isNotEmpty() }.toSet()
}

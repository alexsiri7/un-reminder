package net.interstellarai.unreminder.service.notification

object DurationParser {

    private val pattern = Regex("""(\d+)\s*(hours?|hrs?|h|minutes?|mins?|m|seconds?|secs?|s)\b""", RegexOption.IGNORE_CASE)

    fun parseTotalSeconds(text: String): Int? {
        val match = pattern.find(text) ?: return null
        val amount = match.groupValues[1].toIntOrNull() ?: return null
        val unit = match.groupValues[2].lowercase().first()
        val totalSeconds = when (unit) {
            'h' -> amount * 3600
            'm' -> amount * 60
            's' -> amount
            else -> return null
        }
        return if (totalSeconds > 0) totalSeconds else null
    }
}

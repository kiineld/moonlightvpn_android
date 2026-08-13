package vpn.moonlight.data.remote

/** Display metadata carried in a node's remark, e.g. "🇳🇱 Amsterdam | Europe". */
data class ParsedRemark(
    val name: String?,
    val flag: String?,
    val countryCode: String?,
    val squad: String?,
)

/**
 * Reads the human-facing parts of a node label.
 *
 * Shared by both subscription formats: the share-link fragment and the JSON
 * subscription's `remarks` field carry the same convention.
 */
object RemarkParser {

    fun parse(remark: String?): ParsedRemark {
        val text = remark?.trim().orEmpty()
        if (text.isEmpty()) return ParsedRemark(null, null, null, null)

        val flag = extractFlag(text)
        val label = text.removePrefix(flag.orEmpty()).trim().ifEmpty { null }
        val (name, squad) = splitNameAndSquad(label)

        return ParsedRemark(
            name = name,
            flag = flag,
            countryCode = flag?.let(::flagToCountryCode),
            squad = squad,
        )
    }

    /**
     * A flag emoji is a pair of regional indicator symbols; reading the code
     * points is more reliable than matching a rendered glyph.
     */
    private fun extractFlag(remark: String): String? {
        if (remark.isEmpty()) return null
        val first = remark.codePointAt(0)
        if (!first.isRegionalIndicator()) return null
        val firstLength = Character.charCount(first)
        if (remark.length <= firstLength) return null
        val second = remark.codePointAt(firstLength)
        if (!second.isRegionalIndicator()) return null
        return remark.substring(0, firstLength + Character.charCount(second))
    }

    private fun Int.isRegionalIndicator() = this in 0x1F1E6..0x1F1FF

    private fun flagToCountryCode(flag: String): String? {
        val points = flag.codePoints().toArray()
        if (points.size != 2 || !points.all { it.isRegionalIndicator() }) return null
        return points.joinToString("") { ('A' + (it - 0x1F1E6)).toString() }
    }

    /**
     * A trailing segment after a pipe or middle dot is a group: "Poland ⚡ | Игровой".
     * An arrow is left alone, because "Russia -> Finland" is one name, not two.
     */
    private fun splitNameAndSquad(label: String?): Pair<String?, String?> {
        if (label == null) return null to null
        val separator = listOf(" | ", " · ").firstOrNull { label.contains(it) }
            ?: return label to null
        val parts = label.split(separator).map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size < 2) return label to null
        return parts.first() to parts.last()
    }
}

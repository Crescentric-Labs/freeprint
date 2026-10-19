package crescentric.freeprint.otg

/** A run of text from a changelog line with the inline Markdown it carried: `**bold**` or `` `code` ``. */
class InlineSpan(val text: String, val bold: Boolean = false, val code: Boolean = false)

/** One group of entries inside a release: "Added", "Changed", "Fixed"... */
class ChangelogSection(val title: String, val items: List<String>)

/** One release from CHANGELOG.md. [date] is empty for the Unreleased section. */
class ChangelogRelease(val version: String, val date: String, val note: String, val sections: List<ChangelogSection>)

/**
 * Reads the project's CHANGELOG.md (the Keep a Changelog layout) so the About screen can show it. Only the pieces the file is
 * written with are understood: `## [version] - date` for a release, `### Title` for a group and `- text` for an entry. Plain
 * lines under a release heading become its note, and anything else (the introduction, link definitions) is skipped.
 */
object Changelog {
    /**
     * Splits one changelog line into plain, **bold** and `code` runs, dropping the Markdown markers, so the app can show the same
     * text GitHub shows without stray asterisks and backticks. A marker with no closing partner is kept as written.
     */
    fun spans(line: String): List<InlineSpan> {
        val out = ArrayList<InlineSpan>()
        val plain = StringBuilder()
        fun flush() { if (plain.isNotEmpty()) { out += InlineSpan(plain.toString()); plain.clear() } }
        var i = 0
        while (i < line.length) {
            when {
                line.startsWith("**", i) && line.indexOf("**", i + 2) > i + 2 -> {
                    val end = line.indexOf("**", i + 2)
                    flush(); out += InlineSpan(line.substring(i + 2, end), bold = true); i = end + 2
                }
                line[i] == '`' && line.indexOf('`', i + 1) > i + 1 -> {
                    val end = line.indexOf('`', i + 1)
                    flush(); out += InlineSpan(line.substring(i + 1, end), code = true); i = end + 1
                }
                else -> { plain.append(line[i]); i++ }
            }
        }
        flush()
        return out
    }

    private val releaseLine = Regex("""^##\s+\[?([^\]\s]+)]?(?:\s*-\s*(\S+))?\s*$""")

    fun parse(text: String): List<ChangelogRelease> {
        val releases = ArrayList<ChangelogRelease>()
        var version: String? = null
        var date = ""
        val note = StringBuilder()
        var sections = ArrayList<ChangelogSection>()
        var title: String? = null
        var items = ArrayList<String>()

        fun closeSection() {
            title?.let { sections += ChangelogSection(it, items.toList()) }
            title = null; items = ArrayList()
        }
        fun closeRelease() {
            closeSection()
            version?.let { releases += ChangelogRelease(it, date, note.toString().trim(), sections.toList()) }
            version = null; date = ""; note.clear(); sections = ArrayList()
        }

        for (raw in text.lines()) {
            val line = raw.trimEnd()
            val m = releaseLine.matchEntire(line)
            when {
                m != null -> { closeRelease(); version = m.groupValues[1]; date = m.groupValues[2] }
                version == null -> {}                                  // the introduction above the first release
                line.startsWith("### ") -> { closeSection(); title = line.removePrefix("### ").trim() }
                line.startsWith("- ") && title != null -> items += line.removePrefix("- ").trim()
                line.isNotBlank() && title == null && !line.startsWith("[") -> { if (note.isNotEmpty()) note.append(' '); note.append(line.trim()) }
                line.isNotBlank() && title != null && !line.startsWith("[") && items.isNotEmpty() ->
                    items[items.lastIndex] = items.last() + " " + line.trim()   // an entry continued on the next line
            }
        }
        closeRelease()
        // An Unreleased heading with nothing under it is not worth showing
        return releases.filter { !(it.version.equals("Unreleased", true) && it.sections.isEmpty()) }
    }
}

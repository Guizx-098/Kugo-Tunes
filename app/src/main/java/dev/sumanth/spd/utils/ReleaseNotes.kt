package dev.sumanth.spd.utils

sealed class NoteBlock {
    /** Small coloured group title, written as **New** on its own line. */
    data class Group(val text: String) : NoteBlock()
    data class Bullet(val text: String) : NoteBlock()
    data class Quote(val text: String) : NoteBlock()
    data class Paragraph(val text: String) : NoteBlock()
}

/**
 * Turns the text of a GitHub Release into blocks the update window can draw, in the language of the app.
 * A Release carries one "###" section per language ("What's new" and "Novidades (pt-BR)"); only the one that matches
 * the app language is shown. Releases with a single section (or none) are shown as they are.
 */
object ReleaseNotes {
    private enum class Lang { EN, PT, OTHER }

    fun parse(body: String, languageTag: String): List<NoteBlock> {
        val lines = body.replace("\r", "").lines()

        val sections = mutableListOf<Pair<Lang, MutableList<String>>>()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("###")) {
                sections.add(headingLang(trimmed) to mutableListOf())
            } else if (sections.isNotEmpty()) {
                sections.last().second.add(line)
            }
        }

        val chosen: List<String> = if (sections.isEmpty()) {
            lines
        } else {
            val wanted = if (languageTag == SharedPref.LANG_PT_BR) Lang.PT else Lang.EN
            val section = sections.firstOrNull { it.first == wanted }
                ?: sections.firstOrNull { it.first == Lang.OTHER }
                ?: sections.first()
            section.second
        }
        return toBlocks(chosen)
    }

    private fun headingLang(heading: String): Lang {
        val text = heading.lowercase()
        return when {
            text.contains("pt-br") || text.contains("novidades") || text.contains("portugu") -> Lang.PT
            text.contains("what's new") || text.contains("what\u2019s new") || text.contains("whats new") -> Lang.EN
            else -> Lang.OTHER
        }
    }

    private val groupLine = Regex("^\\*\\*[^*]+\\*\\*$")

    private fun toBlocks(lines: List<String>): List<NoteBlock> = lines.mapNotNull { raw ->
        val t = raw.trim()
        when {
            t.isEmpty() -> null
            // titles and the "Download: Kugo-Tunes.apk (below...)" line are for the GitHub page, not for the app
            t.startsWith("#") -> null
            t.startsWith("**Download") || t.startsWith("**Baixar") -> null
            t.startsWith("- ") || t.startsWith("* ") -> NoteBlock.Bullet(t.drop(2).trim())
            t.startsWith(">") -> NoteBlock.Quote(t.removePrefix(">").trim())
            groupLine.matches(t) -> NoteBlock.Group(t.removeSurrounding("**"))
            else -> NoteBlock.Paragraph(t)
        }
    }
}

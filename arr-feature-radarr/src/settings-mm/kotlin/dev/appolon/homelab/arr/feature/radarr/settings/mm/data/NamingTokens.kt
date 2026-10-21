package dev.appolon.homelab.arr.feature.radarr.settings.mm.data

/**
 * One Radarr naming token (`{Movie Title}` and friends) plus the sample
 * value the live preview substitutes for it. Keys compare
 * case-insensitively after inner whitespace collapses, because Radarr's
 * web UI accepts `{ Movie Title }` spellings when re-editing a format.
 */
data class NamingToken(
    val token: String,
    val sample: String,
) {
    /** `{Movie Title}` normalized for lookup: `movie title`. */
    val key: String = normalizeKey(token)
}

private val whitespaceRuns = Regex("\\s+")

/** Lowercases and collapses inner whitespace so `{ Movie Title }` matches `{Movie Title}`. */
private fun normalizeKey(token: String): String = token
    .removeSurrounding("{", "}")
    .trim()
    .replace(whitespaceRuns, " ")
    .lowercase()

/**
 * Catalog of the tokens Radarr v3 exposes in its naming editor, split the
 * way the web UI splits them: file tokens are valid inside the standard
 * movie format, folder tokens inside the movie folder format. The two
 * lists intentionally overlap; Radarr itself allows the shared subset in
 * both fields.
 */
object NamingTokens {

    val movieFileTokens: List<NamingToken> = listOf(
        NamingToken("{Movie Title}", "Movie Title"),
        NamingToken("{Movie CleanTitle}", "Movie Title"),
        NamingToken("{Movie OriginalTitle}", "Titre Original"),
        NamingToken("{Movie TitleFirstCharacter}", "M"),
        NamingToken("{Movie TitleThe}", "Movie Title, The"),
        NamingToken("{Original FileName}", "Movie.Title.2010.1080p.BluRay.x264-RlsGrp"),
        NamingToken("{Year}", "2010"),
        NamingToken("{Release Year}", "2010"),
        NamingToken("{Quality Title}", "Bluray-1080p"),
        NamingToken("{Quality Full}", "Bluray-1080p Proper"),
        NamingToken("{Quality Proper}", "Proper"),
        NamingToken("{Quality Real}", "REAL"),
        NamingToken("{MediaInfo Simple}", "Bluray x264 DTS"),
        NamingToken("{MediaInfo Full}", "x264 DTS-ES 6.1"),
        NamingToken("{MediaInfo AudioCodec}", "DTS"),
        NamingToken("{MediaInfo AudioChannels}", "5.1"),
        NamingToken("{MediaInfo VideoCodec}", "x264"),
        NamingToken("{MediaInfo VideoBitDepth}", "10"),
        NamingToken("{MediaInfo 3D}", "3D"),
        NamingToken("{Release Group}", "RlsGrp"),
        NamingToken("{Edition}", "IMAX"),
        NamingToken("{Custom Formats}", "Surround Sound"),
        NamingToken("{IMAX}", "IMAX"),
        NamingToken("{IMDb Id}", "tt1234567"),
        NamingToken("{TMDB Id}", "12345"),
    )

    val movieFolderTokens: List<NamingToken> = listOf(
        NamingToken("{Movie Title}", "Movie Title"),
        NamingToken("{Movie CleanTitle}", "Movie Title"),
        NamingToken("{Movie OriginalTitle}", "Titre Original"),
        NamingToken("{Movie TitleFirstCharacter}", "M"),
        NamingToken("{Movie TitleThe}", "Movie Title, The"),
        NamingToken("{Collection}", "Movie Collection"),
        NamingToken("{Quality Title}", "Bluray-1080p"),
        NamingToken("{Quality Full}", "Bluray-1080p Proper"),
        NamingToken("{Year}", "2010"),
        NamingToken("{Release Year}", "2010"),
        NamingToken("{IMDb Id}", "tt1234567"),
        NamingToken("{TMDB Id}", "12345"),
        NamingToken("{Edition Tags}", "IMAX.Edition"),
    )

    private val samplesByKey: Map<String, String> =
        (movieFileTokens + movieFolderTokens).associate { it.key to it.sample }

    /** Sample for a raw `{...}` spelling, or null when the token is unknown. */
    fun sampleFor(raw: String): String? =
        raw.takeIf { it.startsWith("{") && it.endsWith("}") && it.length > 2 }
            ?.let(::normalizeKey)
            ?.let(samplesByKey::get)
}

/**
 * Renders the offline live preview: substitutes every known token with its
 * sample and leaves unknown `{...}` spellings untouched so a typo stays
 * visible in the preview exactly as Radarr would leave it in a filename.
 * Only exact repeats of one separator character collapse (`--` to `-`,
 * double space to single), so a deliberate ` - ` between tokens survives
 * while leftover doubles from chip deletion clean themselves up.
 */
object NamingPreviewRenderer {

    private val braceGroup = Regex("\\{[^{}]*}")
    private val repeatedSeparator = Regex("([-_. ])\\1+")
    private val separators = "-_. ".toCharArray()

    fun render(format: String): String {
        if (format.isBlank()) return ""
        val substituted = braceGroup.replace(format) { match ->
            NamingTokens.sampleFor(match.value) ?: match.value
        }
        val collapsed = repeatedSeparator.replace(substituted) { it.groupValues[1] }
        return collapsed.trim(*separators)
    }
}

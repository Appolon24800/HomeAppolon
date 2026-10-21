package dev.appolon.homelab.arr.feature.sonarr.settings.mm.data

/**
 * One Sonarr naming token (`{Series Title}` and friends) plus the sample
 * value the live preview substitutes for it. Keys compare
 * case-insensitively after inner whitespace collapses, because Sonarr's
 * web UI accepts `{ Season Title }` spellings and lowercase custom
 * formats such as `{season:00}` interchangeably.
 */
data class NamingToken(
    val token: String,
    val sample: String,
) {
    /** `{Season:00}` normalized for lookup: `season:00`. */
    val key: String = normalizeKey(token)
}

private val whitespaceRuns = Regex("\\s+")

/** Lowercases and collapses inner whitespace so `{ Season Title }` matches `{Season Title}`. */
private fun normalizeKey(token: String): String = token
    .removeSurrounding("{", "}")
    .trim()
    .replace(whitespaceRuns, " ")
    .lowercase()

/**
 * Catalog of the tokens Sonarr exposes in its naming editor, split the
 * way the web UI splits them: one chip set per editable format. Sample
 * values mirror Sonarr's own naming example service (series
 * "The Series Title's!" 2010, season 1, episode 1, absolute 1, air date
 * 2013-10-30, WEBDL-1080p Proper, release group RlsGrp) so the offline
 * preview reads exactly like the server's sample.
 */
object NamingTokens {

    /** Tokens valid inside the standard episode format. */
    val standardEpisodeTokens: List<NamingToken> = listOf(
        NamingToken("{Series Title}", "The Series Title's!"),
        NamingToken("{Series CleanTitle}", "The Series Titles!"),
        NamingToken("{Series TitleThe}", "Series Title's!, The"),
        NamingToken("{Series TitleYear}", "The Series Title's! (2010)"),
        NamingToken("{Series TitleWithoutYear}", "The Series Title's!"),
        NamingToken("{Series TitleFirstCharacter}", "S"),
        NamingToken("{Series Year}", "2010"),
        NamingToken("{Season:00}", "01"),
        NamingToken("{Episode:00}", "01"),
        NamingToken("{Episode Title}", "Episode Title (1)"),
        NamingToken("{Episode CleanTitle}", "Episode Title 1"),
        NamingToken("{Original Title}", "The.Series.Title's!.S01E01.1080p.WEBDL.x264-EVOLVE"),
        NamingToken("{Original Filename}", "The.Series.Title's!.S01E01.1080p.WEBDL.x264-EVOLVE.mkv"),
        NamingToken("{Release Group}", "RlsGrp"),
        NamingToken("{Quality Title}", "WEBDL-1080p"),
        NamingToken("{Quality Full}", "WEBDL-1080p Proper"),
        NamingToken("{Quality Proper}", "Proper"),
        NamingToken("{MediaInfo Simple}", "x264 DTS"),
        NamingToken("{MediaInfo Full}", "x264 DTS"),
        NamingToken("{MediaInfo VideoCodec}", "x264"),
        NamingToken("{MediaInfo AudioCodec}", "DTS"),
        NamingToken("{MediaInfo AudioChannels}", "5.1"),
        NamingToken("{Custom Formats}", "Surround Sound x264"),
        NamingToken("{TvdbId}", "12345"),
    )

    /** Tokens valid inside the daily episode format, air-date flavored. */
    val dailyEpisodeTokens: List<NamingToken> = listOf(
        NamingToken("{Series Title}", "The Series Title's!"),
        NamingToken("{Series CleanTitle}", "The Series Titles!"),
        NamingToken("{Series TitleThe}", "Series Title's!, The"),
        NamingToken("{Series TitleYear}", "The Series Title's! (2010)"),
        NamingToken("{Series Year}", "2010"),
        NamingToken("{Air-Date}", "2013-10-30"),
        NamingToken("{Air Date}", "2013 10 30"),
        NamingToken("{yyyy-MM-dd}", "2013-10-30"),
        NamingToken("{Episode Title}", "Episode Title (1)"),
        NamingToken("{Episode CleanTitle}", "Episode Title 1"),
        NamingToken("{Original Title}", "The.Series.Title's!.2013.10.30.1080p.WEBDL.x264-EVOLVE"),
        NamingToken("{Release Group}", "RlsGrp"),
        NamingToken("{Quality Title}", "WEBDL-1080p"),
        NamingToken("{Quality Full}", "WEBDL-1080p Proper"),
        NamingToken("{MediaInfo Simple}", "x264 DTS"),
        NamingToken("{MediaInfo VideoCodec}", "x264"),
        NamingToken("{MediaInfo AudioCodec}", "DTS"),
    )

    /** Tokens valid inside the anime episode format, absolute numbering flavored. */
    val animeEpisodeTokens: List<NamingToken> = listOf(
        NamingToken("{Series Title}", "The Series Title's!"),
        NamingToken("{Series CleanTitle}", "The Series Titles!"),
        NamingToken("{Series TitleThe}", "Series Title's!, The"),
        NamingToken("{Series TitleYear}", "The Series Title's! (2010)"),
        NamingToken("{Series Year}", "2010"),
        NamingToken("{Absolute:000}", "001"),
        NamingToken("{Season:00}", "01"),
        NamingToken("{Episode:00}", "01"),
        NamingToken("{Episode Title}", "Episode Title (1)"),
        NamingToken("{Episode CleanTitle}", "Episode Title 1"),
        NamingToken("{Release Group}", "RlsGrp"),
        NamingToken("{Quality Title}", "WEBDL-1080p"),
        NamingToken("{Quality Full}", "WEBDL-1080p Proper"),
        NamingToken("{MediaInfo Simple}", "x264 DTS"),
        NamingToken("{MediaInfo VideoCodec}", "x264"),
        NamingToken("{MediaInfo AudioCodec}", "DTS"),
    )

    /** Tokens valid inside the series folder format. */
    val seriesFolderTokens: List<NamingToken> = listOf(
        NamingToken("{Series Title}", "The Series Title's!"),
        NamingToken("{Series CleanTitle}", "The Series Titles!"),
        NamingToken("{Series TitleThe}", "Series Title's!, The"),
        NamingToken("{Series TitleYear}", "The Series Title's! (2010)"),
        NamingToken("{Series TitleWithoutYear}", "The Series Title's!"),
        NamingToken("{Series TitleFirstCharacter}", "S"),
        NamingToken("{Series Year}", "2010"),
        NamingToken("{TvdbId}", "12345"),
        NamingToken("{ImdbId}", "tt12345"),
    )

    /** Tokens valid inside the season and specials folder formats. */
    val seasonFolderTokens: List<NamingToken> = listOf(
        NamingToken("{Series Title}", "The Series Title's!"),
        NamingToken("{Series CleanTitle}", "The Series Titles!"),
        NamingToken("{Series Year}", "2010"),
        NamingToken("{Season:00}", "01"),
        NamingToken("{Season Title}", "Part 1"),
        NamingToken("{Season CleanTitle}", "Part 1"),
    )

    /**
     * Extra spellings the renderer accepts even though no chip offers
     * them: unpadded number tokens, the underscore air-date spelling, and
     * single date components Sonarr's daily format allows.
     */
    private val extraSamples = listOf(
        NamingToken("{Season}", "1"),
        NamingToken("{Episode}", "1"),
        NamingToken("{Absolute}", "1"),
        NamingToken("{Absolute:00}", "01"),
        NamingToken("{Air_Date}", "2013-10-30"),
        NamingToken("{yyyy}", "2013"),
        NamingToken("{MM}", "10"),
        NamingToken("{dd}", "30"),
        NamingToken("{Quality Real}", ""),
        NamingToken("{MediaInfo Video}", "x264"),
        NamingToken("{MediaInfo Audio}", "DTS"),
        NamingToken("{MediaInfo VideoBitDepth}", "10"),
        NamingToken("{TvMazeId}", "54321"),
        NamingToken("{TmdbId}", "11223"),
    )

    private val samplesByKey: Map<String, String> = (
        standardEpisodeTokens + dailyEpisodeTokens + animeEpisodeTokens +
            seriesFolderTokens + seasonFolderTokens + extraSamples
        ).associate { it.key to it.sample }

    /** Sample for a raw `{...}` spelling, or null when the token is unknown. */
    fun sampleFor(raw: String): String? =
        raw.takeIf { it.startsWith("{") && it.endsWith("}") && it.length > 2 }
            ?.let(::normalizeKey)
            ?.let(samplesByKey::get)
}

/**
 * Renders the offline live preview: substitutes every known token with its
 * sample and leaves unknown `{...}` spellings untouched so a typo stays
 * visible in the preview exactly as Sonarr would leave it in a filename.
 * Runs of the same separator character collapse to one, matching Sonarr's
 * own filename cleanup, so formats assembled by deleting chips still read
 * like filenames instead of "-- 01"; mixed separators such as " - " stay.
 */
object NamingPreviewRenderer {

    private val braceGroup = Regex("\\{[^{}]*}")
    private val separatorRepeat = Regex("([-_. ])\\1+")
    private val separators = "-_. ".toCharArray()

    fun render(format: String): String {
        if (format.isBlank()) return ""
        val substituted = braceGroup.replace(format) { match ->
            NamingTokens.sampleFor(match.value) ?: match.value
        }
        val collapsed = separatorRepeat.replace(substituted) { it.groupValues[1] }
        return collapsed.trim(*separators)
    }
}

package dev.appolon.homelab.arr.feature.lidarr.settings.mm.data

/**
 * One Lidarr naming token (`{Artist Name}` and friends) plus the sample
 * value the live preview substitutes for it. Keys compare
 * case-insensitively after inner whitespace collapses, because Lidarr's
 * web UI accepts `{ Track Title }` spellings when re-editing a format.
 */
data class NamingToken(
    val token: String,
    val sample: String,
) {
    /** `{Track Title}` normalized for lookup: `track title`. */
    val key: String = normalizeKey(token)
}

private val whitespaceRuns = Regex("\\s+")

/** Lowercases and collapses inner whitespace so `{ Track Title }` matches `{Track Title}`. */
private fun normalizeKey(token: String): String = token
    .removeSurrounding("{", "}")
    .trim()
    .replace(whitespaceRuns, " ")
    .lowercase()

/**
 * Catalog of the tokens Lidarr exposes in its naming editor, split the way
 * the web UI splits them: one chip set per editable format. Sample values
 * mirror Lidarr's own naming example service (artist "The Artist Name",
 * album "The Album Title" 2010, medium 1, track 1, MP3-320 Proper, release
 * group RlsGrp) so the offline preview reads like the server's sample.
 */
object NamingTokens {

    /** Tokens valid inside the standard track format. */
    val standardTrackTokens: List<NamingToken> = listOf(
        NamingToken("{Artist Name}", "The Artist Name"),
        NamingToken("{Artist CleanName}", "The Artist Name"),
        NamingToken("{Artist NameThe}", "Artist Name, The"),
        NamingToken("{Artist MbId}", "a2b3c4d5-1234-5678-9abc-def012345678"),
        NamingToken("{Artist Type}", "Person"),
        NamingToken("{Artist Disambiguation}", "US Rock Band"),
        NamingToken("{Album Title}", "The Album Title"),
        NamingToken("{Album CleanTitle}", "The Album Title"),
        NamingToken("{Album TitleThe}", "Album Title, The"),
        NamingToken("{Album Type}", "Album"),
        NamingToken("{Album ReleaseDate}", "2010-01-05"),
        NamingToken("{Album ReleaseYear}", "2010"),
        NamingToken("{Album MbId}", "b2c3d4e5-2345-6789-abcd-ef0123456789"),
        NamingToken("{Album Disambiguation}", "Deluxe Edition"),
        NamingToken("{Album MediumCount}", "2"),
        NamingToken("{Album TrackCount}", "12"),
        NamingToken("{Track Number}", "1"),
        NamingToken("{Track:00}", "01"),
        NamingToken("{Track Title}", "Track Title (1)"),
        NamingToken("{Track CleanTitle}", "Track Title 1"),
        NamingToken("{Quality Title}", "MP3-320"),
        NamingToken("{Quality Full}", "MP3-320 Proper"),
        NamingToken("{MediaInfo AudioCodec}", "MP3"),
        NamingToken("{MediaInfo AudioChannels}", "2.0"),
        NamingToken("{MediaInfo BitRate}", "320"),
        NamingToken("{MediaInfo BitsPerSample}", "16"),
        NamingToken("{MediaInfo SampleRate}", "44.1"),
        NamingToken("{Release Group}", "RlsGrp"),
        NamingToken("{Original Title}", "The.Artist.Name-The.Album.Title-2010-MP3-320-RlsGrp"),
        NamingToken("{Original Filename}", "01 - Track Title.mp3"),
    )

    /** Tokens valid inside the multi-disc track format, disc flavored. */
    val multiDiscTrackTokens: List<NamingToken> = listOf(
        NamingToken("{Artist Name}", "The Artist Name"),
        NamingToken("{Artist CleanName}", "The Artist Name"),
        NamingToken("{Artist NameThe}", "Artist Name, The"),
        NamingToken("{Album Title}", "The Album Title"),
        NamingToken("{Album CleanTitle}", "The Album Title"),
        NamingToken("{Album TitleThe}", "Album Title, The"),
        NamingToken("{Album Type}", "Album"),
        NamingToken("{Album ReleaseYear}", "2010"),
        NamingToken("{Album DiscCount}", "2"),
        NamingToken("{Album MediumCount}", "2"),
        NamingToken("{Album TrackCount}", "12"),
        NamingToken("{Medium Number}", "1"),
        NamingToken("{Medium:00}", "01"),
        NamingToken("{Track Number}", "1"),
        NamingToken("{Track:00}", "01"),
        NamingToken("{Track Title}", "Track Title (1)"),
        NamingToken("{Track CleanTitle}", "Track Title 1"),
        NamingToken("{Quality Title}", "MP3-320"),
        NamingToken("{Quality Full}", "MP3-320 Proper"),
        NamingToken("{MediaInfo AudioCodec}", "MP3"),
        NamingToken("{Release Group}", "RlsGrp"),
        NamingToken("{Original Filename}", "01 - Track Title.mp3"),
    )

    /** Tokens valid inside the artist folder format. */
    val artistFolderTokens: List<NamingToken> = listOf(
        NamingToken("{Artist Name}", "The Artist Name"),
        NamingToken("{Artist CleanName}", "The Artist Name"),
        NamingToken("{Artist NameThe}", "Artist Name, The"),
        NamingToken("{Artist MbId}", "a2b3c4d5-1234-5678-9abc-def012345678"),
        NamingToken("{Artist Type}", "Person"),
        NamingToken("{Artist Disambiguation}", "US Rock Band"),
    )

    /**
     * Extra spellings the renderer accepts even though no chip offers
     * them: unpadded numbers and single date components Lidarr allows in
     * hand-typed formats.
     */
    private val extraSamples = listOf(
        NamingToken("{Medium}", "1"),
        NamingToken("{Track}", "1"),
        NamingToken("{MediaInfo Bitrate}", "320"),
        NamingToken("{Quality Proper}", "Proper"),
        NamingToken("{Quality Real}", "REAL"),
    )

    private val samplesByKey: Map<String, String> = (
        standardTrackTokens + multiDiscTrackTokens + artistFolderTokens + extraSamples
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
 * visible in the preview exactly as Lidarr would leave it in a filename.
 *
 * Separator runs are preserved verbatim: the server does not collapse
 * `--`, `..`, or double spaces inside a format, so collapsing them here
 * previewed filenames the server would never produce (the same bug was
 * fixed in the Sonarr sibling). Only edge separators trim away, matching
 * Lidarr's cleanup of a filename's outer characters.
 */
object NamingPreviewRenderer {

    private val braceGroup = Regex("\\{[^{}]*}")
    private val separators = "-_. ".toCharArray()

    fun render(format: String): String {
        if (format.isBlank()) return ""
        val substituted = braceGroup.replace(format) { match ->
            NamingTokens.sampleFor(match.value) ?: match.value
        }
        return substituted.trim(*separators)
    }
}

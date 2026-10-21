package dev.appolon.homelab.arr.feature.bazarr.episodes.vm

import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.feature.bazarr.episodes.model.BazarrProviderResult

/** Transient one-line result of a write, surfaced as a snackbar. */
sealed interface SubtitlesMessage {
    data object DownloadSynced : SubtitlesMessage
    data object DownloadQueued : SubtitlesMessage
    data object DeleteSynced : SubtitlesMessage
    data object DeleteQueued : SubtitlesMessage
    data object UploadSynced : SubtitlesMessage
    data class WriteFailed(val error: ArrError) : SubtitlesMessage
}

/** Manual search sheet state for one episode or movie. */
data class ManualSearchState(
    val mediaId: Int,
    val mediaLabel: String,
    val loading: Boolean = false,
    val results: List<BazarrProviderResult> = emptyList(),
    val error: ArrError? = null,
    /** Provider+subtitle pair currently downloading, for row progress. */
    val downloadingKey: String? = null,
) {
    val resultKey: (BazarrProviderResult) -> String
        get() = { "${it.provider}|${it.subtitleId}" }
}

/** Upload sheet state: language selection plus the picked file name. */
data class UploadState(
    val languageCode2: String = "",
    val forced: Boolean = false,
    val hearingImpaired: Boolean = false,
    val fileName: String = "",
    val submitting: Boolean = false,
) {
    val ready: Boolean get() = languageCode2.isNotBlank() && fileName.isNotBlank() && !submitting
}

/** Subtitle content viewer state for one file. */
data class ViewerState(
    val title: String,
    val path: String,
    val loading: Boolean = true,
    val content: String = "",
    val error: ArrError? = null,
)

package dev.appolon.homelab.arr.feature.sonarr.detail.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.api.sonarr.model.EpisodeFileResource
import dev.appolon.homelab.arr.api.sonarr.model.EpisodeResource
import dev.appolon.homelab.arr.api.sonarr.model.HistoryResource
import dev.appolon.homelab.arr.api.sonarr.model.Language
import dev.appolon.homelab.arr.api.sonarr.model.LanguageResource
import dev.appolon.homelab.arr.api.sonarr.model.ManualImportReprocessResource
import dev.appolon.homelab.arr.api.sonarr.model.ManualImportResource
import dev.appolon.homelab.arr.api.sonarr.model.MonitorTypes
import dev.appolon.homelab.arr.api.sonarr.model.MonitoringOptions
import dev.appolon.homelab.arr.api.sonarr.model.Quality
import dev.appolon.homelab.arr.api.sonarr.model.QualityModel
import dev.appolon.homelab.arr.api.sonarr.model.QualityProfileResource
import dev.appolon.homelab.arr.api.sonarr.model.ReleaseResource
import dev.appolon.homelab.arr.api.sonarr.model.RenameEpisodeResource
import dev.appolon.homelab.arr.api.sonarr.model.SeasonPassResource
import dev.appolon.homelab.arr.api.sonarr.model.SeasonPassSeriesResource
import dev.appolon.homelab.arr.api.sonarr.model.SeasonResource
import dev.appolon.homelab.arr.api.sonarr.model.SeriesResource
import dev.appolon.homelab.arr.api.sonarr.model.SeriesTypes
import dev.appolon.homelab.arr.api.sonarr.model.TagResource
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.feature.sonarr.detail.data.DetailCommand
import dev.appolon.homelab.arr.feature.sonarr.detail.data.SonarrCommandBodies
import dev.appolon.homelab.arr.feature.sonarr.detail.data.SonarrSeriesDetailRepository
import dev.appolon.homelab.arr.feature.sonarr.library.data.WriteOutcome
import dev.appolon.homelab.arr.feature.sonarr.library.data.parseTagIds
import dev.appolon.homelab.arr.net.arrError
import dev.appolon.homelab.arr.ui.lce.LceState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The two content tabs of the series detail screen. */
enum class DetailTab { SEASONS, HISTORY }

/** What the manual search sheet is currently searching for. */
sealed interface SearchTarget {
    data class Episode(val episodeId: Int, val label: String) : SearchTarget
    data class Season(val seasonNumber: Int) : SearchTarget
}

/** Form state of the series edit sheet. */
data class SeriesEditForm(
    val monitored: Boolean,
    val seriesType: SeriesTypes,
    val qualityProfileId: Int,
    val path: String,
    val seasonFolder: Boolean,
    val tagsText: String,
    val moveFiles: Boolean = false,
    val tagsError: Boolean = false,
)

/** Form state of the episode file editor. */
data class EpisodeFileForm(
    val fileId: Int,
    val episodeId: Int,
    val qualityId: Int,
    val languageIds: Set<Int>,
    val releaseGroup: String,
    val original: EpisodeFileResource,
)

/** Form state of the per-series monitoring strategy sheet. */
data class SeasonPassForm(
    val monitored: Boolean,
    val monitor: MonitorTypes = MonitorTypes.ALL,
    val ignoreEpisodesWithFiles: Boolean = false,
    val ignoreEpisodesWithoutFiles: Boolean = false,
)

/** Rename preview sheet state. */
data class RenamePreviewState(
    val rows: List<RenameEpisodeResource> = emptyList(),
    val loading: Boolean = true,
    val error: ArrError? = null,
)

/** Interactive import sheet state. */
data class ImportState(
    val seasonNumber: Int? = null,
    val candidates: List<ManualImportResource> = emptyList(),
    val selected: Set<Int> = emptySet(),
    val loading: Boolean = true,
    val error: ArrError? = null,
)

/** Transient one-line result of a write, surfaced as a snackbar. */
sealed interface DetailMessage {
    data object WriteSynced : DetailMessage
    data object WriteQueued : DetailMessage
    data object SearchStarted : DetailMessage
    data object SearchQueued : DetailMessage
    data object GrabSent : DetailMessage
    data object GrabQueued : DetailMessage
    data class ImportSynced(val count: Int) : DetailMessage
    data object ImportQueued : DetailMessage
    data object RenameStarted : DetailMessage
    data object RenameQueued : DetailMessage
    data object DeleteSynced : DetailMessage
    data object DeleteQueued : DetailMessage
    data class WriteFailed(val error: ArrError) : DetailMessage
}

/**
 * State of the series detail screen. Derived views (season list, the
 * selected season's episodes, per-episode history) are computed properties
 * so presentation logic stays testable without Compose.
 */
data class SeriesDetailUiState(
    val seriesId: Int,
    val loaded: Boolean = false,
    val loadError: ArrError? = null,
    val series: SeriesResource? = null,
    val qualityProfiles: List<QualityProfileResource> = emptyList(),
    val qualities: List<Quality> = emptyList(),
    val languages: List<LanguageResource> = emptyList(),
    val tags: List<TagResource> = emptyList(),
    val tab: DetailTab = DetailTab.SEASONS,
    val selectedSeason: Int? = null,
    val episodes: List<EpisodeResource> = emptyList(),
    val history: List<HistoryResource> = emptyList(),
    val historyRefreshing: Boolean = false,
    val searchTarget: SearchTarget? = null,
    val releases: List<ReleaseResource> = emptyList(),
    val releasesLoading: Boolean = false,
    val releasesError: ArrError? = null,
    val fileForm: EpisodeFileForm? = null,
    val editForm: SeriesEditForm? = null,
    val seasonPassForm: SeasonPassForm? = null,
    val renamePreview: RenamePreviewState? = null,
    val importState: ImportState? = null,
    val confirmDelete: Boolean = false,
    val deleted: Boolean = false,
    val queuedForServer: Int = 0,
    val queuedForSeries: Boolean = false,
    val refreshing: Boolean = false,
    val message: DetailMessage? = null,
) {
    /** Seasons sorted with specials last, mirroring the web UI. */
    val seasons: List<SeasonResource>
        get() = series?.seasons.orEmpty()
            .sortedWith(compareBy({ it.seasonNumber == 0 }, { it.seasonNumber }))

    /** Episodes of the selected season, in air order. */
    val seasonEpisodes: List<EpisodeResource>
        get() = episodes
            .filter { it.seasonNumber == selectedSeason }
            .sortedBy { it.episodeNumber }

    fun seasonOf(seasonNumber: Int): SeasonResource? =
        seasons.firstOrNull { it.seasonNumber == seasonNumber }

    fun historyFor(episodeId: Int): List<HistoryResource> =
        history.filter { it.episodeId == episodeId }.sortedByDescending { it.date }

    fun qualityLabel(id: Int): String =
        qualities.firstOrNull { it.id == id }?.name ?: "#$id"

    fun languageLabel(id: Int): String =
        languages.firstOrNull { it.id == id }?.name ?: "#$id"

    fun profileLabel(id: Int): String =
        qualityProfiles.firstOrNull { it.id == id }?.name ?: "#$id"

    fun tagLabel(id: Int): String = tags.firstOrNull { it.id == id }?.label ?: "#$id"
}

/**
 * ViewModel of the Sonarr series detail screen. Owns no Android types so
 * unit tests construct it directly against fakes; the destination composable
 * wires it to the data graph singletons.
 */
class SeriesDetailViewModel(
    private val seriesId: Int,
    private val repository: SonarrSeriesDetailRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SeriesDetailUiState(seriesId = seriesId))
    val state: StateFlow<SeriesDetailUiState> = _state.asStateFlow()

    val lce: StateFlow<LceState<SeriesDetailUiState>> = state
        .map { s ->
            when {
                s.deleted -> LceState.Empty
                s.loadError != null && s.series == null -> LceState.Error(s.loadError)
                !s.loaded -> LceState.Loading
                s.series == null -> LceState.Empty
                else -> LceState.Content(s, refreshing = s.refreshing)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LceState.Loading)

    init {
        viewModelScope.launch {
            repository.observeSeries(seriesId)
                .catch { t -> _state.update { it.copy(loaded = true, loadError = t.arrError()) } }
                .collect { series ->
                    _state.update { s ->
                        s.copy(
                            series = series,
                            loaded = true,
                            selectedSeason = s.selectedSeason ?: defaultSeasonOf(series),
                        )
                    }
                }
        }
        viewModelScope.launch {
            repository.observeEpisodes(seriesId)
                .catch { }
                .collect { episodes -> _state.update { it.copy(episodes = episodes) } }
        }
        viewModelScope.launch {
            repository.observeHistory(seriesId)
                .catch { }
                .collect { history -> _state.update { it.copy(history = history) } }
        }
        viewModelScope.launch {
            repository.queuedCount()
                .catch { }
                .collect { count -> _state.update { it.copy(queuedForServer = count) } }
        }
        viewModelScope.launch {
            repository.queuedSeriesIds()
                .catch { }
                .collect { ids ->
                    _state.update { it.copy(queuedForSeries = seriesId in ids) }
                }
        }
        viewModelScope.launch { initialLoad() }
    }

    /** Cache-first entry: refreshes only when the shared cache is absent or stale. */
    private suspend fun initialLoad() {
        val cached = repository.cachedSeries(seriesId)
        if (cached == null || repository.seriesStale()) {
            refreshAll()
        } else {
            refreshLookups()
        }
    }

    /** Pull-to-refresh and error retry: refetch everything for this series. */
    fun refresh() {
        viewModelScope.launch { refreshAll() }
    }

    private suspend fun refreshAll() {
        _state.update { it.copy(refreshing = true) }
        when (val series = repository.refreshSeries(seriesId)) {
            is ArrResult.Err -> _state.update {
                it.copy(refreshing = false, loadError = series.error)
            }

            is ArrResult.Ok -> {
                _state.update { it.copy(refreshing = false, loadError = null) }
                refreshEpisodes()
                refreshHistoryFeed()
                refreshLookups()
            }
        }
    }

    private suspend fun refreshEpisodes() {
        when (val result = repository.refreshEpisodes(seriesId)) {
            is ArrResult.Err -> if (_state.value.episodes.isEmpty()) {
                _state.update { it.copy(loadError = result.error) }
            }

            is ArrResult.Ok -> Unit
        }
    }

    fun setTab(tab: DetailTab) = _state.update { it.copy(tab = tab) }

    fun selectSeason(seasonNumber: Int?) =
        _state.update { it.copy(selectedSeason = seasonNumber) }

    fun toggleSeriesMonitored() {
        val series = _state.value.series ?: return
        viewModelScope.launch {
            handleOutcome(
                prefix = Prefix.WRITE,
                outcome = repository.setSeriesMonitored(series, !series.monitored),
            )
        }
    }

    fun toggleSeasonMonitored(seasonNumber: Int) {
        val series = _state.value.series ?: return
        val current = _state.value.seasonOf(seasonNumber)?.monitored ?: return
        viewModelScope.launch {
            handleOutcome(
                prefix = Prefix.WRITE,
                outcome = repository.setSeasonMonitored(series, seasonNumber, !current),
            )
        }
    }

    fun toggleEpisodeMonitored(episode: EpisodeResource) {
        viewModelScope.launch {
            handleOutcome(
                prefix = Prefix.WRITE,
                outcome = repository.setEpisodesMonitored(
                    seriesId,
                    listOf(episode.id),
                    !episode.monitored,
                ),
            )
        }
    }

    fun refreshMetadata() = runCommand(
        SonarrCommandBodies.refreshSeries(seriesId),
        Prefix.SEARCH,
    )

    fun scanDisk() = runCommand(SonarrCommandBodies.rescanSeries(seriesId), Prefix.SEARCH)

    fun searchSeries() = runCommand(SonarrCommandBodies.seriesSearch(seriesId), Prefix.SEARCH)

    fun searchSeason(seasonNumber: Int) = runCommand(
        SonarrCommandBodies.seasonSearch(seriesId, seasonNumber),
        Prefix.SEARCH,
    )

    fun searchEpisode(episode: EpisodeResource) = runCommand(
        SonarrCommandBodies.episodesSearch(listOf(episode.id)),
        Prefix.SEARCH,
    )

    private fun runCommand(body: String, prefix: Prefix) {
        viewModelScope.launch {
            val outcome = repository.runCommandBody(body, describeCommand(body))
            handleOutcome(prefix, outcome)
        }
    }

    private fun describeCommand(body: String): String = when {
        body.contains(DetailCommand.REFRESH_METADATA.wireName) -> "Refresh series metadata"
        body.contains(DetailCommand.SCAN_DISK.wireName) -> "Rescan series folder"
        body.contains(DetailCommand.SERIES_SEARCH.wireName) -> "Search series"
        body.contains(DetailCommand.SEASON_SEARCH.wireName) -> "Search season"
        body.contains(DetailCommand.EPISODES_SEARCH.wireName) -> "Search episodes"
        else -> "Sonarr command"
    }

    fun openManualSearchEpisode(episode: EpisodeResource) {
        _state.update {
            it.copy(
                searchTarget = SearchTarget.Episode(episode.id, episode.label),
                releases = emptyList(),
                releasesLoading = true,
                releasesError = null,
            )
        }
        loadReleases()
    }

    fun openManualSearchSeason(seasonNumber: Int) {
        _state.update {
            it.copy(
                searchTarget = SearchTarget.Season(seasonNumber),
                releases = emptyList(),
                releasesLoading = true,
                releasesError = null,
            )
        }
        loadReleases()
    }

    fun closeManualSearch() =
        _state.update { it.copy(searchTarget = null, releases = emptyList(), releasesError = null) }

    fun refreshReleases() {
        _state.update { it.copy(releasesLoading = true, releasesError = null) }
        loadReleases()
    }

    private fun loadReleases() {
        val target = _state.value.searchTarget ?: return
        viewModelScope.launch {
            val result = repository.releases(
                seriesId = seriesId,
                episodeId = (target as? SearchTarget.Episode)?.episodeId,
                seasonNumber = (target as? SearchTarget.Season)?.seasonNumber,
            )
            _state.update { s ->
                when (result) {
                    is ArrResult.Ok -> s.copy(
                        releases = result.value.sortedByDescending { it.ageMinutes },
                        releasesLoading = false,
                    )

                    is ArrResult.Err -> s.copy(
                        releasesLoading = false,
                        releasesError = result.error,
                    )
                }
            }
        }
    }

    fun grabRelease(release: ReleaseResource) {
        viewModelScope.launch {
            handleOutcome(Prefix.GRAB, repository.grabRelease(release))
        }
    }

    fun openRenamePreview() {
        _state.update { it.copy(renamePreview = RenamePreviewState()) }
        viewModelScope.launch {
            _state.update { s ->
                when (val result = repository.renamePreview(seriesId)) {
                    is ArrResult.Ok -> s.copy(
                        renamePreview = RenamePreviewState(rows = result.value, loading = false),
                    )

                    is ArrResult.Err -> s.copy(
                        renamePreview = RenamePreviewState(loading = false, error = result.error),
                    )
                }
            }
        }
    }

    fun closeRenamePreview() = _state.update { it.copy(renamePreview = null) }

    /** Runs the series-wide rename; the preview sheet closes on dispatch. */
    fun runRename() {
        viewModelScope.launch {
            val outcome = repository.runCommandBody(
                SonarrCommandBodies.renameFiles(seriesId),
                "Rename files for series $seriesId",
            )
            closeRenamePreview()
            handleOutcome(Prefix.RENAME, outcome)
        }
    }

    fun openImport(seasonNumber: Int? = null) {
        _state.update { it.copy(importState = ImportState(seasonNumber = seasonNumber)) }
        viewModelScope.launch {
            val series = _state.value.series
            val result = repository.importCandidates(
                folder = series?.path,
                seriesId = seriesId,
                seasonNumber = seasonNumber,
            )
            _state.update { s ->
                val current = s.importState ?: return@update s
                when (result) {
                    is ArrResult.Ok -> s.copy(
                        importState = current.copy(
                            candidates = result.value,
                            loading = false,
                        ),
                    )

                    is ArrResult.Err -> s.copy(
                        importState = current.copy(loading = false, error = result.error),
                    )
                }
            }
        }
    }

    fun closeImport() = _state.update { it.copy(importState = null) }

    fun toggleImportSelected(id: Int) = _state.update { s ->
        val current = s.importState ?: return@update s
        val next = if (id in current.selected) current.selected - id else current.selected + id
        s.copy(importState = current.copy(selected = next))
    }

    fun importSelected() {
        val current = _state.value.importState ?: return
        val chosen = current.candidates.filter { it.id in current.selected }
        if (chosen.isEmpty()) return
        val payload = chosen.map { it.toReprocess(seriesId) }
        viewModelScope.launch {
            when (val outcome = repository.importItems(payload)) {
                is ArrResult.Ok -> _state.update { s ->
                    s.copy(
                        importState = null,
                        message = if (outcome.value is WriteOutcome.Queued) {
                            DetailMessage.ImportQueued
                        } else {
                            DetailMessage.ImportSynced(chosen.size)
                        },
                    )
                }

                is ArrResult.Err -> _state.update {
                    it.copy(message = DetailMessage.WriteFailed(outcome.error))
                }
            }
        }
    }

    fun openFileEditor(episode: EpisodeResource) {
        val file = episode.episodeFile
        _state.update {
            it.copy(
                fileForm = EpisodeFileForm(
                    fileId = episode.episodeFileId,
                    episodeId = episode.id,
                    qualityId = file.quality.quality.id,
                    languageIds = file.languages.orEmpty().map { lang -> lang.id }.toSet(),
                    releaseGroup = file.releaseGroup.orEmpty(),
                    original = file,
                ),
            )
        }
    }

    fun closeFileEditor() = _state.update { it.copy(fileForm = null) }

    fun updateFileEditor(transform: (EpisodeFileForm) -> EpisodeFileForm) =
        _state.update { s ->
            val form = s.fileForm ?: return@update s
            s.copy(fileForm = transform(form))
        }

    fun submitFileEditor() {
        val form = _state.value.fileForm ?: return
        val state = _state.value
        val file = form.original.copy(
            quality = QualityModel(
                quality = Quality(
                    id = form.qualityId,
                    name = state.qualities.firstOrNull { it.id == form.qualityId }?.name,
                ),
            ),
            languages = form.languageIds.sorted().map { id ->
                Language(id = id, name = state.languages.firstOrNull { it.id == id }?.name)
            },
            releaseGroup = form.releaseGroup.trim().ifEmpty { null },
        )
        viewModelScope.launch {
            handleOutcome(Prefix.WRITE, repository.updateEpisodeFile(file))
            _state.update { it.copy(fileForm = null) }
        }
    }

    fun deleteFileFromEditor() {
        val form = _state.value.fileForm ?: return
        viewModelScope.launch {
            handleOutcome(Prefix.WRITE, repository.deleteEpisodeFile(form.fileId))
            _state.update { it.copy(fileForm = null) }
        }
    }

    fun openEdit() {
        val series = _state.value.series ?: return
        _state.update {
            it.copy(
                editForm = SeriesEditForm(
                    monitored = series.monitored,
                    seriesType = series.seriesType,
                    qualityProfileId = series.qualityProfileId,
                    path = series.path.orEmpty(),
                    seasonFolder = series.seasonFolder,
                    tagsText = series.tags.orEmpty().sorted().joinToString(","),
                ),
            )
        }
    }

    fun closeEdit() = _state.update { it.copy(editForm = null) }

    fun updateEdit(transform: (SeriesEditForm) -> SeriesEditForm) =
        _state.update { s ->
            val form = s.editForm ?: return@update s
            s.copy(editForm = transform(form))
        }

    fun submitEdit() {
        val form = _state.value.editForm ?: return
        val series = _state.value.series ?: return
        val tagIds = parseTagIds(form.tagsText)
        if (tagIds == null) {
            _state.update { s -> s.copy(editForm = form.copy(tagsError = true)) }
            return
        }
        val edited = series.copy(
            monitored = form.monitored,
            seriesType = form.seriesType,
            qualityProfileId = form.qualityProfileId,
            path = form.path.trim().ifEmpty { null },
            seasonFolder = form.seasonFolder,
            tags = if (form.tagsText.isBlank()) series.tags else tagIds.toSet(),
        )
        viewModelScope.launch {
            handleOutcome(Prefix.WRITE, repository.updateSeries(edited, form.moveFiles))
            _state.update { it.copy(editForm = null) }
        }
    }

    fun openSeasonPass() {
        val series = _state.value.series ?: return
        _state.update { it.copy(seasonPassForm = SeasonPassForm(monitored = series.monitored)) }
    }

    fun closeSeasonPass() = _state.update { it.copy(seasonPassForm = null) }

    fun updateSeasonPass(transform: (SeasonPassForm) -> SeasonPassForm) =
        _state.update { s ->
            val form = s.seasonPassForm ?: return@update s
            s.copy(seasonPassForm = transform(form))
        }

    /** Applies the monitoring strategy sheet to this one series. */
    fun submitSeasonPass() {
        val form = _state.value.seasonPassForm ?: return
        val series = _state.value.series ?: return
        val pass = SeasonPassResource(
            series = listOf(
                SeasonPassSeriesResource(
                    id = series.id,
                    monitored = form.monitored,
                    seasons = series.seasons.orEmpty().map { it.copy(monitored = form.monitored) },
                ),
            ),
            monitoringOptions = MonitoringOptions(
                ignoreEpisodesWithFiles = form.ignoreEpisodesWithFiles,
                ignoreEpisodesWithoutFiles = form.ignoreEpisodesWithoutFiles,
                monitor = form.monitor,
            ),
        )
        viewModelScope.launch {
            handleOutcome(Prefix.WRITE, repository.applySeasonPass(pass))
            _state.update { it.copy(seasonPassForm = null) }
        }
    }

    fun requestDelete() = _state.update { it.copy(confirmDelete = true) }

    fun dismissDelete() = _state.update { it.copy(confirmDelete = false) }

    fun confirmDelete(deleteFiles: Boolean, addImportListExclusion: Boolean) {
        _state.update { it.copy(confirmDelete = false) }
        viewModelScope.launch {
            when (val outcome = repository.deleteSeries(seriesId, deleteFiles, addImportListExclusion)) {
                is ArrResult.Ok -> _state.update { s ->
                    s.copy(
                        deleted = true,
                        message = if (outcome.value is WriteOutcome.Queued) {
                            DetailMessage.DeleteQueued
                        } else {
                            DetailMessage.DeleteSynced
                        },
                    )
                }

                is ArrResult.Err -> _state.update {
                    it.copy(message = DetailMessage.WriteFailed(outcome.error))
                }
            }
        }
    }

    fun refreshHistory() {
        viewModelScope.launch {
            _state.update { it.copy(historyRefreshing = true) }
            repository.refreshHistory(seriesId)
            _state.update { it.copy(historyRefreshing = false) }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    private suspend fun refreshLookups() {
        repository.qualityProfiles().let { result ->
            if (result is ArrResult.Ok) {
                _state.update { it.copy(qualityProfiles = result.value) }
            }
        }
        repository.qualityDefinitions().let { result ->
            if (result is ArrResult.Ok) {
                _state.update {
                    it.copy(qualities = result.value.mapNotNull { def -> def.quality.takeIf { q -> q.id != 0 } })
                }
            }
        }
        repository.languages().let { result ->
            if (result is ArrResult.Ok) {
                _state.update { it.copy(languages = result.value) }
            }
        }
        repository.tags().let { result ->
            if (result is ArrResult.Ok) {
                _state.update { it.copy(tags = result.value) }
            }
        }
    }

    private suspend fun refreshHistoryFeed() {
        repository.refreshHistory(seriesId)
    }

    private fun handleOutcome(prefix: Prefix, outcome: ArrResult<WriteOutcome>) {
        _state.update { s ->
            when (outcome) {
                is ArrResult.Ok -> s.copy(
                    message = when {
                        outcome.value is WriteOutcome.Queued && prefix == Prefix.SEARCH ->
                            DetailMessage.SearchQueued

                        outcome.value is WriteOutcome.Queued && prefix == Prefix.GRAB ->
                            DetailMessage.GrabQueued

                        outcome.value is WriteOutcome.Queued && prefix == Prefix.RENAME ->
                            DetailMessage.RenameQueued

                        outcome.value is WriteOutcome.Queued -> DetailMessage.WriteQueued

                        prefix == Prefix.SEARCH -> DetailMessage.SearchStarted
                        prefix == Prefix.GRAB -> DetailMessage.GrabSent
                        prefix == Prefix.RENAME -> DetailMessage.RenameStarted
                        else -> DetailMessage.WriteSynced
                    },
                )

                is ArrResult.Err -> s.copy(message = DetailMessage.WriteFailed(outcome.error))
            }
        }
    }

    private enum class Prefix { WRITE, SEARCH, GRAB, RENAME }

    private fun defaultSeasonOf(series: SeriesResource?): Int? {
        val seasons = series?.seasons.orEmpty()
        return (seasons.firstOrNull { it.seasonNumber != 0 } ?: seasons.firstOrNull())?.seasonNumber
    }
}

private val EpisodeResource.label: String
    get() = "S$seasonNumber E$episodeNumber"

/** Maps one import candidate onto the reprocess payload the import call takes. */
internal fun ManualImportResource.toReprocess(seriesId: Int): ManualImportReprocessResource =
    ManualImportReprocessResource(
        id = id,
        path = path,
        seriesId = seriesId,
        seasonNumber = seasonNumber,
        episodes = episodes,
        quality = quality,
        languages = languages,
        releaseGroup = releaseGroup,
        downloadId = downloadId,
        customFormats = customFormats,
        customFormatScore = customFormatScore,
        indexerFlags = indexerFlags,
        releaseType = releaseType,
    )

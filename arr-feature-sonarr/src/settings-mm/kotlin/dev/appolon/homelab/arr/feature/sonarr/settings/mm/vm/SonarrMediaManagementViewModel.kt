package dev.appolon.homelab.arr.feature.sonarr.settings.mm.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.api.sonarr.model.EpisodeTitleRequiredType
import dev.appolon.homelab.arr.api.sonarr.model.FileDateType
import dev.appolon.homelab.arr.api.sonarr.model.MediaManagementConfigResource
import dev.appolon.homelab.arr.api.sonarr.model.ProperDownloadTypes
import dev.appolon.homelab.arr.api.sonarr.model.RescanAfterRefreshType
import dev.appolon.homelab.arr.api.sonarr.model.SeasonPackUpgradeType
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.sonarr.library.data.WriteOutcome
import dev.appolon.homelab.arr.feature.sonarr.settings.mm.data.SonarrMediaManagementRepository
import dev.appolon.homelab.arr.ui.lce.LceState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Editable draft of the media management config. Text and number fields
 * live as strings so partially typed values never crash the editor; the
 * numeric parse happens once at save time and falls back to the loaded
 * value when the field is blank or malformed.
 */
data class MediaManagementDraft(
    val autoUnmonitorPreviouslyDownloadedEpisodes: Boolean = false,
    val createEmptySeriesFolders: Boolean = false,
    val deleteEmptyFolders: Boolean = false,
    val downloadPropersAndRepacks: ProperDownloadTypes = ProperDownloadTypes.PREFER_AND_UPGRADE,
    val fileDate: FileDateType = FileDateType.NONE,
    val rescanAfterRefresh: RescanAfterRefreshType = RescanAfterRefreshType.ALWAYS,
    val episodeTitleRequired: EpisodeTitleRequiredType = EpisodeTitleRequiredType.ALWAYS,
    val skipFreeSpaceCheckWhenGrabbing: Boolean = false,
    val skipFreeSpaceCheckWhenImporting: Boolean = false,
    val minimumFreeSpaceWhenImporting: String = "0",
    val copyUsingHardlinks: Boolean = false,
    val useScriptImport: Boolean = false,
    val scriptImportPath: String = "",
    val importExtraFiles: Boolean = false,
    val extraFileExtensions: String = "",
    val userRejectedExtensions: String = "",
    val seasonPackUpgrade: SeasonPackUpgradeType = SeasonPackUpgradeType.ALL,
    val seasonPackUpgradeThreshold: String = "0.9",
    val recycleBin: String = "",
    val recycleBinCleanupDays: String = "0",
    val setPermissionsLinux: Boolean = false,
    val chmodFolder: String = "",
    val chownGroup: String = "",
    val enableMediaInfo: Boolean = false,
) {

    /** Fills a draft from the wire config, keeping string fields as strings. */
    constructor(config: MediaManagementConfigResource) : this(
        autoUnmonitorPreviouslyDownloadedEpisodes = config.autoUnmonitorPreviouslyDownloadedEpisodes,
        createEmptySeriesFolders = config.createEmptySeriesFolders,
        deleteEmptyFolders = config.deleteEmptyFolders,
        downloadPropersAndRepacks = config.downloadPropersAndRepacks
            ?: ProperDownloadTypes.PREFER_AND_UPGRADE,
        fileDate = config.fileDate ?: FileDateType.NONE,
        rescanAfterRefresh = config.rescanAfterRefresh ?: RescanAfterRefreshType.ALWAYS,
        episodeTitleRequired = config.episodeTitleRequired ?: EpisodeTitleRequiredType.ALWAYS,
        skipFreeSpaceCheckWhenGrabbing = config.skipFreeSpaceCheckWhenGrabbing,
        skipFreeSpaceCheckWhenImporting = config.skipFreeSpaceCheckWhenImporting,
        minimumFreeSpaceWhenImporting = config.minimumFreeSpaceWhenImporting.toString(),
        copyUsingHardlinks = config.copyUsingHardlinks,
        useScriptImport = config.useScriptImport,
        scriptImportPath = config.scriptImportPath ?: "",
        importExtraFiles = config.importExtraFiles,
        extraFileExtensions = config.extraFileExtensions ?: "",
        userRejectedExtensions = config.userRejectedExtensions ?: "",
        seasonPackUpgrade = config.seasonPackUpgrade ?: SeasonPackUpgradeType.ALL,
        seasonPackUpgradeThreshold = config.seasonPackUpgradeThreshold?.toString() ?: "0.9",
        recycleBin = config.recycleBin ?: "",
        recycleBinCleanupDays = config.recycleBinCleanupDays.toString(),
        setPermissionsLinux = config.setPermissionsLinux,
        chmodFolder = config.chmodFolder ?: "",
        chownGroup = config.chownGroup ?: "",
        enableMediaInfo = config.enableMediaInfo,
    )

    /** Applies the draft onto a loaded config, keeping the server id. */
    fun applyOnto(config: MediaManagementConfigResource): MediaManagementConfigResource =
        config.copy(
            autoUnmonitorPreviouslyDownloadedEpisodes = autoUnmonitorPreviouslyDownloadedEpisodes,
            createEmptySeriesFolders = createEmptySeriesFolders,
            deleteEmptyFolders = deleteEmptyFolders,
            downloadPropersAndRepacks = downloadPropersAndRepacks,
            fileDate = fileDate,
            rescanAfterRefresh = rescanAfterRefresh,
            episodeTitleRequired = episodeTitleRequired,
            skipFreeSpaceCheckWhenGrabbing = skipFreeSpaceCheckWhenGrabbing,
            skipFreeSpaceCheckWhenImporting = skipFreeSpaceCheckWhenImporting,
            minimumFreeSpaceWhenImporting = minimumFreeSpaceWhenImporting.toIntOrNull()
                ?: config.minimumFreeSpaceWhenImporting,
            copyUsingHardlinks = copyUsingHardlinks,
            useScriptImport = useScriptImport,
            scriptImportPath = scriptImportPath.takeUnless { it.isBlank() },
            importExtraFiles = importExtraFiles,
            extraFileExtensions = extraFileExtensions.takeUnless { it.isBlank() },
            userRejectedExtensions = userRejectedExtensions.takeUnless { it.isBlank() },
            seasonPackUpgrade = seasonPackUpgrade,
            seasonPackUpgradeThreshold = seasonPackUpgradeThreshold.toDoubleOrNull()
                ?: config.seasonPackUpgradeThreshold,
            recycleBin = recycleBin.takeUnless { it.isBlank() },
            recycleBinCleanupDays = recycleBinCleanupDays.toIntOrNull()
                ?: config.recycleBinCleanupDays,
            setPermissionsLinux = setPermissionsLinux,
            chmodFolder = chmodFolder.takeUnless { it.isBlank() },
            chownGroup = chownGroup.takeUnless { it.isBlank() },
            enableMediaInfo = enableMediaInfo,
        )
}

/** Content payload of the media management LCE state. */
data class SonarrMediaManagementContent(
    val draft: MediaManagementDraft,
    val dirty: Boolean,
    val saving: Boolean,
    val pendingActions: Int,
)

/** Transient results of saving, mapped to strings by the screen. */
enum class MediaManagementNotice {
    SAVED,
    SAVED_QUEUED,
}

/**
 * Media management toggles screen state. The config loads cache-first
 * through the repository; every edit updates the draft and save PUTs the
 * whole config. When the server is unreachable the save applies to the
 * cache optimistically and enqueues for replay, so the toggles always
 * reflect the user's intent.
 */
class SonarrMediaManagementViewModel(
    private val serverId: ServerId,
    private val repository: SonarrMediaManagementRepository,
) : ViewModel() {

    data class UiState(
        val loaded: Boolean = false,
        val loadError: ArrError? = null,
        val refreshing: Boolean = false,
        val draft: MediaManagementDraft = MediaManagementDraft(),
        val dirty: Boolean = false,
        val saving: Boolean = false,
        val pendingActions: Int = 0,
        val notice: MediaManagementNotice? = null,
        val writeError: ArrError? = null,
        /** Set while the filesystem picker writes its chosen path into the draft. */
        val pickedField: String? = null,
    )

    private var loadedConfig: MediaManagementConfigResource = MediaManagementConfigResource()

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    val lce: StateFlow<LceState<SonarrMediaManagementContent>> = state
        .map { s ->
            when {
                s.loadError != null -> LceState.Error(s.loadError)
                !s.loaded -> LceState.Loading
                else -> LceState.Content(
                    SonarrMediaManagementContent(
                        draft = s.draft,
                        dirty = s.dirty,
                        saving = s.saving,
                        pendingActions = s.pendingActions,
                    ),
                    refreshing = s.refreshing,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LceState.Loading)

    init {
        viewModelScope.launch {
            repository.observeMediaManagement().collect { config ->
                if (config != null) {
                    loadedConfig = config
                    // A background refresh never camps on the user's unsaved
                    // edits; it only re-seeds the draft when nothing is dirty.
                    _state.update { s ->
                        if (s.dirty) {
                            s.copy(loaded = true, loadError = null)
                        } else {
                            s.copy(loaded = true, loadError = null, draft = MediaManagementDraft(config))
                        }
                    }
                }
            }
        }
        viewModelScope.launch {
            repository.queuedCount().collect { pending ->
                _state.update { it.copy(pendingActions = pending) }
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true) }
            when (val result = repository.refreshIfStale()) {
                is ArrResult.Ok -> _state.update { it.copy(refreshing = false) }

                is ArrResult.Err -> _state.update {
                    it.copy(
                        loaded = true,
                        loadError = if (!it.loaded) result.error else null,
                        writeError = if (it.loaded) result.error else null,
                        refreshing = false,
                    )
                }
            }
        }
    }

    fun edit(transform: (MediaManagementDraft) -> MediaManagementDraft) {
        _state.update { it.copy(draft = transform(it.draft), dirty = true) }
    }

    fun setPickedField(field: String?) {
        _state.update { it.copy(pickedField = field) }
    }

    /** Writes a path chosen in the filesystem browser into the draft field. */
    fun applyPickedPath(path: String) {
        val field = _state.value.pickedField ?: return
        edit { draft ->
            when (field) {
                FIELD_RECYCLE_BIN -> draft.copy(recycleBin = path)
                FIELD_SCRIPT_IMPORT -> draft.copy(scriptImportPath = path)
                else -> draft
            }
        }
        setPickedField(null)
    }

    fun save() {
        val current = _state.value
        if (!current.dirty || current.saving) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            when (val result = repository.saveMediaManagement(current.draft.applyOnto(loadedConfig))) {
                is ArrResult.Ok -> _state.update {
                    it.copy(
                        saving = false,
                        dirty = false,
                        notice = if (result.value is WriteOutcome.Queued) {
                            MediaManagementNotice.SAVED_QUEUED
                        } else {
                            MediaManagementNotice.SAVED
                        },
                        writeError = null,
                    )
                }

                is ArrResult.Err -> _state.update {
                    it.copy(saving = false, notice = null, writeError = result.error)
                }
            }
        }
    }

    fun consumeNotice() {
        _state.update { it.copy(notice = null) }
    }

    fun consumeWriteError() {
        _state.update { it.copy(writeError = null) }
    }

    companion object {
        const val FIELD_RECYCLE_BIN = "recycleBin"
        const val FIELD_SCRIPT_IMPORT = "scriptImportPath"
    }
}

package dev.appolon.homelab.arr.feature.lidarr.settings.quality.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.api.lidarr.model.MetadataProviderConfigResource
import dev.appolon.homelab.arr.api.lidarr.model.WriteAudioTagsType
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.feature.lidarr.library.data.WriteOutcome
import dev.appolon.homelab.arr.feature.lidarr.settings.quality.data.LidarrQualityRepository
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
 * Editable draft of the metadata provider config, the single-row settings
 * screen Lidarr has and Sonarr does not. `writeAudioTags` picks how far
 * tag writing goes; scrub and cover art only apply while tags are written.
 */
data class MetadataProviderDraft(
    val configId: Int = 0,
    val metadataSource: String = "",
    val writeAudioTags: WriteAudioTagsType = WriteAudioTagsType.NO,
    val scrubAudioTags: Boolean = false,
    val embedCoverArt: Boolean = false,
    val isExisting: Boolean = false,
) {
    fun toResource(): MetadataProviderConfigResource = MetadataProviderConfigResource(
        id = configId,
        metadataSource = metadataSource,
        writeAudioTags = writeAudioTags,
        scrubAudioTags = scrubAudioTags,
        embedCoverArt = embedCoverArt,
    )

    companion object {
        fun from(resource: MetadataProviderConfigResource): MetadataProviderDraft =
            MetadataProviderDraft(
                configId = resource.id,
                metadataSource = resource.metadataSource.orEmpty(),
                writeAudioTags = resource.writeAudioTags ?: WriteAudioTagsType.NO,
                scrubAudioTags = resource.scrubAudioTags,
                embedCoverArt = resource.embedCoverArt,
                isExisting = true,
            )
    }
}

/** Content payload of the metadata provider config LCE state. */
data class LidarrMetadataProviderContent(
    val draft: MetadataProviderDraft = MetadataProviderDraft(),
    val dirty: Boolean = false,
    val saving: Boolean = false,
    val pendingActions: Int = 0,
)

/**
 * Metadata provider config screen state: one config row loaded cache-first,
 * edited locally, saved through the config PUT. Offline saves apply
 * optimistically to the cache and queue.
 */
class LidarrMetadataProviderViewModel(
    private val repository: LidarrQualityRepository,
) : ViewModel() {

    data class UiState(
        val loaded: Boolean = false,
        val loadError: ArrError? = null,
        val draft: MetadataProviderDraft = MetadataProviderDraft(),
        val dirty: Boolean = false,
        val saving: Boolean = false,
        val pendingActions: Int = 0,
        val savedNotice: Boolean = false,
        val savedQueued: Boolean = false,
        val writeError: ArrError? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    val lce: StateFlow<LceState<LidarrMetadataProviderContent>> = state
        .map { s ->
            when {
                s.loadError != null -> LceState.Error(s.loadError)
                !s.loaded -> LceState.Loading
                else -> LceState.Content(
                    LidarrMetadataProviderContent(
                        draft = s.draft,
                        dirty = s.dirty,
                        saving = s.saving,
                        pendingActions = s.pendingActions,
                    ),
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LceState.Loading)

    init {
        viewModelScope.launch {
            repository.queuedCount().collect { pending ->
                _state.update { it.copy(pendingActions = pending) }
            }
        }
        load()
    }

    private fun load() {
        viewModelScope.launch {
            repository.refreshIfStale()
            val cached = repository.cachedMetadataProviderConfig()
            when {
                // A missing config row is only an error when nothing is
                // cached; the screen then shows the blank defaults so the
                // first save still works.
                cached != null -> _state.update {
                    it.copy(
                        loaded = true,
                        loadError = null,
                        draft = MetadataProviderDraft.from(cached),
                    )
                }

                else -> _state.update { it.copy(loaded = true, loadError = null) }
            }
        }
    }

    fun refresh() {
        load()
    }

    fun edit(transform: (MetadataProviderDraft) -> MetadataProviderDraft) {
        _state.update { it.copy(draft = transform(it.draft), dirty = true) }
    }

    fun setMetadataSource(source: String) {
        edit { it.copy(metadataSource = source) }
    }

    fun setWriteAudioTags(mode: WriteAudioTagsType) {
        edit { draft ->
            // Scrubbing and embedding only matter while tags are written.
            if (mode == WriteAudioTagsType.NO) {
                draft.copy(writeAudioTags = mode, scrubAudioTags = false, embedCoverArt = false)
            } else {
                draft.copy(writeAudioTags = mode)
            }
        }
    }

    fun setScrubAudioTags(scrub: Boolean) {
        edit { it.copy(scrubAudioTags = scrub) }
    }

    fun setEmbedCoverArt(embed: Boolean) {
        edit { it.copy(embedCoverArt = embed) }
    }

    fun save() {
        val current = _state.value
        if (!current.dirty || current.saving) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            when (val result = repository.saveMetadataProviderConfig(current.draft.toResource())) {
                is ArrResult.Ok -> _state.update {
                    it.copy(
                        saving = false,
                        dirty = false,
                        savedNotice = result.value is WriteOutcome.Synced,
                        savedQueued = result.value is WriteOutcome.Queued,
                        writeError = null,
                    )
                }

                is ArrResult.Err -> _state.update {
                    it.copy(saving = false, writeError = result.error)
                }
            }
        }
    }

    fun consumeSavedNotice() {
        _state.update { it.copy(savedNotice = false, savedQueued = false) }
    }

    fun consumeWriteError() {
        _state.update { it.copy(writeError = null) }
    }
}

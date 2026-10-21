package dev.appolon.homelab.arr.feature.prowlarr.indexers.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.api.prowlarr.model.AppProfileResource
import dev.appolon.homelab.arr.api.prowlarr.model.ApplicationResource
import dev.appolon.homelab.arr.api.prowlarr.model.ApplicationSyncLevel
import dev.appolon.homelab.arr.api.prowlarr.model.Field
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerResource
import dev.appolon.homelab.arr.api.prowlarr.model.TagResource
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.feature.prowlarr.indexers.data.FieldForms
import dev.appolon.homelab.arr.feature.prowlarr.indexers.data.ProwlarrIndexersRepository
import dev.appolon.homelab.arr.feature.prowlarr.indexers.data.WriteOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement

/** Transient one-line result, surfaced as a snackbar. */
sealed interface ApplicationsMessage {
    data object Saved : ApplicationsMessage
    data object SavedQueued : ApplicationsMessage
    data object Deleted : ApplicationsMessage
    data object DeleteQueued : ApplicationsMessage
    data object TestPassed : ApplicationsMessage
    data class TestFailed(val error: ArrError) : ApplicationsMessage
    data object SyncStarted : ApplicationsMessage
    data object SyncQueued : ApplicationsMessage
    data class SyncFailed(val error: ArrError) : ApplicationsMessage
    data object ProfileSaved : ApplicationsMessage
    data object ProfileDeleted : ApplicationsMessage
    data class WriteFailed(val error: ArrError) : ApplicationsMessage
    data object NameMissing : ApplicationsMessage
}

/** Editor form of one application, schema based when adding. */
data class ApplicationEditorForm(
    val editing: ApplicationResource? = null,
    val schema: ApplicationResource? = null,
    val fields: List<Field> = emptyList(),
    val values: Map<String, JsonElement> = emptyMap(),
    val name: String = "",
    val syncLevel: ApplicationSyncLevel = ApplicationSyncLevel.DISABLED,
    val tagLabels: List<String> = emptyList(),
    val nameMissing: Boolean = false,
)

/** Editor form of one app profile. */
data class AppProfileEditorForm(
    val editing: AppProfileResource? = null,
    val name: String = "",
    val minimumSeeders: Int = 1,
    val enableRss: Boolean = true,
    val enableAutomaticSearch: Boolean = true,
    val enableInteractiveSearch: Boolean = true,
    val nameMissing: Boolean = false,
)

/** One line of the sync preview dialog. */
data class SyncPreviewRow(
    val indexerName: String,
    val enabled: Boolean,
    val appProfileLabel: String,
)

/** State of the applications screen. */
data class ApplicationsUiState(
    val loaded: Boolean = false,
    val loadError: ArrError? = null,
    val refreshing: Boolean = false,
    val applications: List<ApplicationResource> = emptyList(),
    val schemas: List<ApplicationResource> = emptyList(),
    val appProfiles: List<AppProfileResource> = emptyList(),
    val indexers: List<IndexerResource> = emptyList(),
    val tags: List<TagResource> = emptyList(),
    val editor: ApplicationEditorForm? = null,
    val profileEditor: AppProfileEditorForm? = null,
    val profilesShowing: Boolean = false,
    val confirmDeleteId: Int? = null,
    val confirmProfileDeleteId: Int? = null,
    val syncPreviewFor: ApplicationResource? = null,
    val syncPreview: List<SyncPreviewRow> = emptyList(),
    val testing: Boolean = false,
    val saving: Boolean = false,
    val message: ApplicationsMessage? = null,
) {
    fun tagLabels(application: ApplicationResource): List<String> =
        application.tags.orEmpty().mapNotNull { id -> tags.firstOrNull { it.id == id }?.label }

    fun profileLabel(id: Int): String =
        appProfiles.firstOrNull { it.id == id }?.name ?: "#$id"
}

/**
 * ViewModel of the applications screen: CRUD over applications with the
 * generic field model, a client-computed sync preview, and the app profile
 * editors that control how synced indexers behave in each target app.
 */
class ApplicationsViewModel(
    private val repository: ProwlarrIndexersRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ApplicationsUiState())
    val state: StateFlow<ApplicationsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeApplications()
                .catch { }
                .collect { apps -> _state.update { it.copy(applications = apps, loaded = true) } }
        }
        viewModelScope.launch {
            repository.observeIndexers()
                .catch { }
                .collect { indexers -> _state.update { it.copy(indexers = indexers) } }
        }
        viewModelScope.launch {
            repository.observeTags()
                .catch { }
                .collect { tags -> _state.update { it.copy(tags = tags) } }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true) }
            // The sync preview reads the indexer list, so keep it fresh
            // alongside the applications themselves.
            repository.refreshIfStale()
            when (val result = repository.refreshApplications()) {
                is ArrResult.Ok -> _state.update { it.copy(refreshing = false, loadError = null) }
                is ArrResult.Err ->
                    _state.update { it.copy(refreshing = false, loadError = result.error) }
            }
            when (val schemas = repository.applicationSchemas()) {
                is ArrResult.Ok -> _state.update { it.copy(schemas = schemas.value) }
                is ArrResult.Err -> Unit
            }
            when (val profiles = repository.appProfiles()) {
                is ArrResult.Ok -> _state.update { it.copy(appProfiles = profiles.value) }
                is ArrResult.Err -> Unit
            }
        }
    }

    /** Triggers the ApplicationSync command on the server, or queues it. */
    fun syncAll() {
        viewModelScope.launch {
            when (val outcome = repository.syncApplications()) {
                is ArrResult.Ok -> _state.update { s ->
                    s.copy(
                        message = if (outcome.value is WriteOutcome.Queued) {
                            ApplicationsMessage.SyncQueued
                        } else {
                            ApplicationsMessage.SyncStarted
                        },
                    )
                }
                is ArrResult.Err ->
                    _state.update { it.copy(message = ApplicationsMessage.SyncFailed(outcome.error)) }
            }
        }
    }

    /** Opens the sync preview: which indexers land in the target app. */
    fun openSyncPreview(application: ApplicationResource) = _state.update { s ->
        val rows = s.indexers
            .sortedByDescending { it.enable }
            .map { indexer ->
                SyncPreviewRow(
                    indexerName = indexer.name.orEmpty(),
                    enabled = indexer.enable,
                    appProfileLabel = s.profileLabel(indexer.appProfileId),
                )
            }
        s.copy(syncPreviewFor = application, syncPreview = rows)
    }

    fun closeSyncPreview() =
        _state.update { it.copy(syncPreviewFor = null, syncPreview = emptyList()) }

    fun openAdd() {
        val schema = _state.value.schemas.firstOrNull() ?: return
        _state.update {
            it.copy(
                editor = ApplicationEditorForm(
                    schema = schema,
                    fields = schema.fields.orEmpty(),
                    values = FieldForms.editableValues(schema.fields),
                    syncLevel = schema.syncLevel,
                ),
            )
        }
    }

    fun edit(application: ApplicationResource) {
        _state.update { s ->
            s.copy(
                editor = ApplicationEditorForm(
                    editing = application,
                    fields = application.fields.orEmpty(),
                    values = FieldForms.editableValues(application.fields),
                    name = application.name.orEmpty(),
                    syncLevel = application.syncLevel,
                    tagLabels = s.tagLabels(application),
                ),
            )
        }
    }

    fun closeEditor() = _state.update { it.copy(editor = null) }

    fun setFieldValue(name: String, value: JsonElement) =
        _state.update { s ->
            val form = s.editor ?: return@update s
            s.copy(editor = form.copy(values = form.values + (name to value)))
        }

    fun setName(text: String) =
        _state.update { s ->
            val form = s.editor ?: return@update s
            s.copy(editor = form.copy(name = text, nameMissing = false))
        }

    fun setSyncLevel(level: ApplicationSyncLevel) =
        _state.update { s ->
            val form = s.editor ?: return@update s
            s.copy(editor = form.copy(syncLevel = level))
        }

    fun toggleTagLabel(label: String) =
        _state.update { s ->
            val form = s.editor ?: return@update s
            val next = if (label in form.tagLabels) form.tagLabels - label else form.tagLabels + label
            s.copy(editor = form.copy(tagLabels = next))
        }

    fun save() {
        val form = _state.value.editor ?: return
        if (form.name.isBlank()) {
            _state.update { s ->
                s.copy(
                    editor = form.copy(nameMissing = true),
                    message = ApplicationsMessage.NameMissing,
                )
            }
            return
        }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val tagIds = when (val result = repository.ensureTagIds(form.tagLabels)) {
                is ArrResult.Ok -> result.value
                is ArrResult.Err -> {
                    _state.update {
                        it.copy(saving = false, message = ApplicationsMessage.WriteFailed(result.error))
                    }
                    return@launch
                }
            }
            val base = form.editing ?: form.schema ?: return@launch
            val resource = base.copy(
                fields = FieldForms.applyValues(base.fields, form.values),
                name = form.name.trim(),
                syncLevel = form.syncLevel,
                tags = tagIds,
            )
            when (val outcome = repository.saveApplication(resource)) {
                is ArrResult.Ok -> _state.update { s ->
                    s.copy(
                        saving = false,
                        editor = null,
                        message = if (outcome.value is WriteOutcome.Queued) {
                            ApplicationsMessage.SavedQueued
                        } else {
                            ApplicationsMessage.Saved
                        },
                    )
                }
                is ArrResult.Err -> _state.update {
                    it.copy(saving = false, message = ApplicationsMessage.WriteFailed(outcome.error))
                }
            }
        }
    }

    fun test() {
        val form = _state.value.editor ?: return
        val base = form.editing ?: form.schema ?: return
        val resource = base.copy(
            fields = FieldForms.applyValues(base.fields, form.values),
            name = form.name.trim(),
            syncLevel = form.syncLevel,
        )
        _state.update { it.copy(testing = true) }
        viewModelScope.launch {
            when (val result = repository.testApplication(resource)) {
                is ArrResult.Ok -> _state.update {
                    it.copy(testing = false, message = ApplicationsMessage.TestPassed)
                }
                is ArrResult.Err -> _state.update {
                    it.copy(testing = false, message = ApplicationsMessage.TestFailed(result.error))
                }
            }
        }
    }

    fun requestDelete(id: Int) = _state.update { it.copy(confirmDeleteId = id) }

    fun dismissDelete() = _state.update { it.copy(confirmDeleteId = null) }

    fun confirmDelete() {
        val id = _state.value.confirmDeleteId ?: return
        _state.update { it.copy(confirmDeleteId = null) }
        viewModelScope.launch {
            when (val outcome = repository.deleteApplication(id)) {
                is ArrResult.Ok -> _state.update { s ->
                    s.copy(
                        editor = null,
                        message = if (outcome.value is WriteOutcome.Queued) {
                            ApplicationsMessage.DeleteQueued
                        } else {
                            ApplicationsMessage.Deleted
                        },
                    )
                }
                is ArrResult.Err ->
                    _state.update { it.copy(message = ApplicationsMessage.WriteFailed(outcome.error)) }
            }
        }
    }

    fun showProfiles(showing: Boolean) = _state.update { it.copy(profilesShowing = showing) }

    fun openProfileEditor(profile: AppProfileResource?) = _state.update {
        it.copy(
            profileEditor = if (profile == null) {
                AppProfileEditorForm()
            } else {
                AppProfileEditorForm(
                    editing = profile,
                    name = profile.name.orEmpty(),
                    minimumSeeders = profile.minimumSeeders,
                    enableRss = profile.enableRss,
                    enableAutomaticSearch = profile.enableAutomaticSearch,
                    enableInteractiveSearch = profile.enableInteractiveSearch,
                )
            },
        )
    }

    fun closeProfileEditor() = _state.update { it.copy(profileEditor = null) }

    fun setProfileName(text: String) =
        _state.update { s ->
            val form = s.profileEditor ?: return@update s
            s.copy(profileEditor = form.copy(name = text, nameMissing = false))
        }

    fun setProfileMinimumSeeders(value: Int) =
        _state.update { s ->
            val form = s.profileEditor ?: return@update s
            s.copy(profileEditor = form.copy(minimumSeeders = value))
        }

    fun setProfileEnableRss(value: Boolean) =
        _state.update { s ->
            val form = s.profileEditor ?: return@update s
            s.copy(profileEditor = form.copy(enableRss = value))
        }

    fun setProfileEnableAutomaticSearch(value: Boolean) =
        _state.update { s ->
            val form = s.profileEditor ?: return@update s
            s.copy(profileEditor = form.copy(enableAutomaticSearch = value))
        }

    fun setProfileEnableInteractiveSearch(value: Boolean) =
        _state.update { s ->
            val form = s.profileEditor ?: return@update s
            s.copy(profileEditor = form.copy(enableInteractiveSearch = value))
        }

    fun saveProfile() {
        val form = _state.value.profileEditor ?: return
        if (form.name.isBlank()) {
            _state.update { s ->
                s.copy(
                    profileEditor = form.copy(nameMissing = true),
                    message = ApplicationsMessage.NameMissing,
                )
            }
            return
        }
        viewModelScope.launch {
            val resource = (form.editing ?: AppProfileResource()).copy(
                name = form.name.trim(),
                minimumSeeders = form.minimumSeeders,
                enableRss = form.enableRss,
                enableAutomaticSearch = form.enableAutomaticSearch,
                enableInteractiveSearch = form.enableInteractiveSearch,
            )
            when (val outcome = repository.saveAppProfile(resource)) {
                is ArrResult.Ok -> _state.update {
                    it.copy(
                        profileEditor = null,
                        message = if (outcome.value is WriteOutcome.Queued) {
                            ApplicationsMessage.SavedQueued
                        } else {
                            ApplicationsMessage.ProfileSaved
                        },
                    )
                }
                is ArrResult.Err ->
                    _state.update { it.copy(message = ApplicationsMessage.WriteFailed(outcome.error)) }
            }
            refreshProfiles()
        }
    }

    fun requestProfileDelete(id: Int) = _state.update { it.copy(confirmProfileDeleteId = id) }

    fun dismissProfileDelete() = _state.update { it.copy(confirmProfileDeleteId = null) }

    fun confirmProfileDelete() {
        val id = _state.value.confirmProfileDeleteId ?: return
        _state.update { it.copy(confirmProfileDeleteId = null) }
        viewModelScope.launch {
            when (val outcome = repository.deleteAppProfile(id)) {
                is ArrResult.Ok -> _state.update {
                    it.copy(message = if (outcome.value is WriteOutcome.Queued) {
                        ApplicationsMessage.DeleteQueued
                    } else {
                        ApplicationsMessage.ProfileDeleted
                    })
                }
                is ArrResult.Err ->
                    _state.update { it.copy(message = ApplicationsMessage.WriteFailed(outcome.error)) }
            }
            refreshProfiles()
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    private suspend fun refreshProfiles() {
        when (val profiles = repository.appProfiles(ttlMillis = 0)) {
            is ArrResult.Ok -> _state.update { it.copy(appProfiles = profiles.value) }
            is ArrResult.Err -> Unit
        }
    }
}

package dev.appolon.homelab.arr.feature.prowlarr.settings.vm

import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.api.prowlarr.FakeProwlarrIndexers
import dev.appolon.homelab.arr.api.prowlarr.FakeProwlarrSettings
import dev.appolon.homelab.arr.api.prowlarr.FakeProwlarrSystem
import dev.appolon.homelab.arr.api.prowlarr.model.DownloadClientCategory
import dev.appolon.homelab.arr.api.prowlarr.model.DownloadClientResource
import dev.appolon.homelab.arr.api.prowlarr.model.Field
import dev.appolon.homelab.arr.api.prowlarr.model.NotificationResource
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.prowlarr.settings.FakeSettingsActionQueue
import dev.appolon.homelab.arr.feature.prowlarr.settings.FakeSettingsEntityStore
import dev.appolon.homelab.arr.feature.prowlarr.settings.settingsRepository
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProwlarrSettingsViewModelsTest {

    private val serverId = ServerId("s1")
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var fake: FakeProwlarrSettings
    private lateinit var indexers: FakeProwlarrIndexers
    private lateinit var system: FakeProwlarrSystem
    private lateinit var store: FakeSettingsEntityStore
    private lateinit var queue: FakeSettingsActionQueue

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        fake = FakeProwlarrSettings()
        indexers = FakeProwlarrIndexers()
        system = FakeProwlarrSystem()
        store = FakeSettingsEntityStore()
        queue = FakeSettingsActionQueue()
        fake.downloadClients[1] = DownloadClientResource(
            id = 1,
            name = "qBittorrent",
            enable = true,
            priority = 1,
            supportsCategories = true,
            categories = listOf(DownloadClientCategory(clientCategory = "tv", categories = listOf(5000))),
        )
        fake.downloadClients[2] = DownloadClientResource(
            id = 2,
            name = "SABnzbd",
            enable = false,
            priority = 2,
        )
        fake.notifications[5] = NotificationResource(
            id = 5,
            name = "Discord",
            supportsOnGrab = true,
            onGrab = true,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun repository() = settingsRepository(
        fake,
        indexers,
        system,
        store,
        queue,
        serverId = serverId,
        now = { 1_000L },
        io = dispatcher,
    )

    private fun runVm(block: suspend () -> Unit) = runTest(dispatcher) {
        block()
    }

    // ------------------------------------------------------- download clients

    @Test
    fun `clients vm loads rows sorted by priority`() = runTest(dispatcher) {
        val vm = ProwlarrDownloadClientsViewModel(serverId, repository())
        assertEquals(listOf("qBittorrent", "SABnzbd"), vm.state.value.clients.map { it.name })
        vm.viewModelScope.coroutineContext.cancelChildren()
    }

    @Test
    fun `clients vm reports a failed test all`() = runTest(dispatcher) {
        fake.errorStatus = { if (it.operation == "testAllDownloadClients") 500 else null }
        val vm = ProwlarrDownloadClientsViewModel(serverId, repository())
        vm.testAll()
        assertEquals(DownloadClientsNotice.TEST_ALL_FAILED, vm.state.value.notice)
        assertNotNull(vm.state.value.writeError)
        vm.viewModelScope.coroutineContext.cancelChildren()
    }

    @Test
    fun `clients vm enable toggle queues offline`() = runTest(dispatcher) {
        val vm = ProwlarrDownloadClientsViewModel(serverId, repository())
        fake.failure = { throw IOException("offline") }
        vm.setEnabled(vm.state.value.clients.first { it.id == 2 }, true)
        assertEquals(DownloadClientsNotice.TOGGLE_QUEUED, vm.state.value.notice)
        assertEquals(1, queue.pending().count { it.path.startsWith("api/v1/downloadclient") })
        vm.viewModelScope.coroutineContext.cancelChildren()
    }

    // --------------------------------------------------- download client editor

    @Test
    fun `client editor starts at the picker for new rows`() = runTest(dispatcher) {
        fake.downloadClientSchemas += DownloadClientResource(
            implementation = "QBittorrent",
            implementationName = "qBittorrent",
        )
        val vm = ProwlarrDownloadClientEditorViewModel(serverId, null, repository())
        assertTrue(vm.state.value.picker)
        assertEquals(1, vm.state.value.schemas.size)
        vm.viewModelScope.coroutineContext.cancelChildren()
    }

    @Test
    fun `client editor edits grab targets and saves them`() = runTest(dispatcher) {
        val vm = ProwlarrDownloadClientEditorViewModel(serverId, 1, repository())
        val draft = vm.state.value.draft
        assertNotNull(draft)
        assertEquals(1, draft!!.targets.size)
        assertEquals("tv", draft.targets.first().clientCategory)

        vm.addGrabTarget()
        vm.setGrabTargetCategory(1, "movies")
        vm.toggleGrabTargetNewznabCategory(1, 2000)
        var saved: Boolean = false
        vm.save { saved = true }

        assertTrue(saved)
        val stored = fake.downloadClients[1]?.categories.orEmpty()
        assertEquals(listOf("tv", "movies"), stored.mapNotNull { it.clientCategory })
        assertEquals(listOf(2000), stored[1].categories)
        vm.viewModelScope.coroutineContext.cancelChildren()
    }

    @Test
    fun `client editor creates tag ids for new labels`() = runTest(dispatcher) {
        indexers.tags[4] = dev.appolon.homelab.arr.api.prowlarr.model.TagResource(id = 4, label = "tv")
        fake.downloadClientSchemas += DownloadClientResource(
            implementation = "Deluge",
            implementationName = "Deluge",
            supportsCategories = true,
        )
        val vm = ProwlarrDownloadClientEditorViewModel(serverId, null, repository())
        vm.pickSchema(fake.downloadClientSchemas.first())
        vm.updateDraft { it.copy(name = "Deluge") }
        vm.toggleTag("tv")
        vm.toggleTag("fresh")
        vm.save {}

        val created = fake.downloadClients.values.single { it.name == "Deluge" }
        assertEquals(listOf(4, 101), created.tags)
        vm.viewModelScope.coroutineContext.cancelChildren()
    }

    // ------------------------------------------------------------- notifications

    @Test
    fun `notifications vm loads rows`() = runTest(dispatcher) {
        val vm = ProwlarrNotificationsViewModel(serverId, repository())
        assertEquals(listOf("Discord"), vm.state.value.notifications.map { it.name })
        vm.viewModelScope.coroutineContext.cancelChildren()
    }

    @Test
    fun `notifications vm delete queues offline`() = runTest(dispatcher) {
        val vm = ProwlarrNotificationsViewModel(serverId, repository())
        fake.failure = { throw IOException("offline") }
        vm.requestDelete(vm.state.value.notifications.first())
        vm.deleteConfirmed()
        assertEquals(NotificationsNotice.DELETED_QUEUED, vm.state.value.notice)
        assertEquals(
            "api/v1/notification/5",
            queue.pending().single { it.method == "DELETE" }.path,
        )
        vm.viewModelScope.coroutineContext.cancelChildren()
    }

    @Test
    fun `notification editor toggles events and saves`() = runTest(dispatcher) {
        val vm = ProwlarrNotificationEditorViewModel(serverId, 5, repository())
        val draft = vm.state.value.draft
        assertNotNull(draft)
        assertEquals(listOf("onGrab", "includeManualGrabs"), draft!!.events.map { it.key })

        vm.toggleEvent("onHealthRestored") // unsupported here, ignored by the matrix
        vm.toggleEvent("includeManualGrabs")
        var saved = false
        vm.save { saved = true }

        assertTrue(saved)
        assertTrue(fake.notifications[5]?.includeManualGrabs == true)
        assertFalse(fake.notifications[5]?.onHealthIssue == true)
        vm.viewModelScope.coroutineContext.cancelChildren()
    }

    @Test
    fun `notification editor picks a schema and carries field values`() = runTest(dispatcher) {
        fake.notificationSchemas += NotificationResource(
            implementation = "Webhook",
            implementationName = "Webhook",
            fields = listOf(
                Field(name = "url", label = "URL", type = "text", value = null),
            ),
        )
        val vm = ProwlarrNotificationEditorViewModel(serverId, null, repository())
        vm.pickSchema(fake.notificationSchemas.first())
        vm.onValueChange("url", kotlinx.serialization.json.JsonPrimitive("https://example.test"))
        vm.updateDraft { it.copy(name = "Hook") }
        vm.save {}

        val created = fake.notifications.values.single { it.name == "Hook" }
        val urlField = created.fields.orEmpty().single { it.name == "url" }
        assertEquals(
            "https://example.test",
            (urlField.value as kotlinx.serialization.json.JsonPrimitive).content,
        )
        vm.viewModelScope.coroutineContext.cancelChildren()
    }

    // ----------------------------------------------------------- general + system

    @Test
    fun `general vm seeds drafts from the host config`() = runTest(dispatcher) {
        fake.hostConfig = dev.appolon.homelab.arr.api.prowlarr.model.HostConfigResource(
            bindAddress = "127.0.0.1",
            port = 9696,
            urlBase = "/prowlarr",
            username = "admin",
        )
        fake.developmentConfig =
            dev.appolon.homelab.arr.api.prowlarr.model.DevelopmentConfigResource(logSql = true)
        fake.localizationOptions += dev.appolon.homelab.arr.api.prowlarr.model.LocalizationOption(
            name = "English",
            value = "en",
        )
        val vm = ProwlarrGeneralSettingsViewModel(repository())
        val state = vm.state.value
        assertEquals("127.0.0.1", state.hostDraft.bindAddress)
        assertEquals("9696", state.hostDraft.port)
        assertEquals("/prowlarr", state.hostDraft.urlBase)
        assertTrue(state.developmentDraft.logSql)
        assertEquals(listOf("en"), state.localizationOptions.mapNotNull { it.value })
        vm.viewModelScope.coroutineContext.cancelChildren()
    }

    @Test
    fun `general vm saves the host draft without echoing secrets`() = runTest(dispatcher) {
        val vm = ProwlarrGeneralSettingsViewModel(repository())
        vm.editHost { it.copy(port = "8123", password = "new-secret") }
        vm.saveHost()

        assertEquals(8123, fake.hostConfig.port)
        assertEquals(null, fake.hostConfig.password)
        assertFalse(vm.state.value.hostDirty)
        vm.viewModelScope.coroutineContext.cancelChildren()
    }

    @Test
    fun `general vm rejects an invalid port`() = runTest(dispatcher) {
        val vm = ProwlarrGeneralSettingsViewModel(repository())
        vm.editHost { it.copy(port = "not-a-port") }
        vm.saveHost()

        assertEquals(0, fake.hostConfig.port)
        assertTrue(vm.state.value.hostDirty)
        vm.viewModelScope.coroutineContext.cancelChildren()
    }

    @Test
    fun `general vm queues the host save offline`() = runTest(dispatcher) {
        val vm = ProwlarrGeneralSettingsViewModel(repository())
        fake.failure = { throw IOException("offline") }
        vm.editHost { it.copy(port = "8123") }
        vm.saveHost()

        assertEquals(GeneralSettingsNotice.HOST_SAVED_QUEUED, vm.state.value.notice)
        assertEquals(
            "api/v1/config/host/1",
            queue.pending().single { it.path.startsWith("api/v1/config/host") }.path,
        )
        vm.viewModelScope.coroutineContext.cancelChildren()
    }

    @Test
    fun `general vm saves the ui culture`() = runTest(dispatcher) {
        fake.localizationOptions += dev.appolon.homelab.arr.api.prowlarr.model.LocalizationOption(
            name = "Deutsch",
            value = "de",
        )
        val vm = ProwlarrGeneralSettingsViewModel(repository())
        vm.setUiLanguage("de")
        vm.saveCulture()

        assertEquals("de", fake.uiConfig.uiLanguage)
        assertFalse(vm.state.value.cultureDirty)
        vm.viewModelScope.coroutineContext.cancelChildren()
    }

    @Test
    fun `system vm loads status and tasks`() = runTest(dispatcher) {
        system.status = dev.appolon.homelab.arr.api.prowlarr.model.SystemResource(
            appName = "Prowlarr",
            version = "1.31",
        )
        system.tasks[7] = dev.appolon.homelab.arr.api.prowlarr.model.TaskResource(
            id = 7,
            name = "Application Check Update",
        )
        system.health += dev.appolon.homelab.arr.api.prowlarr.model.HealthResource(
            id = 1,
            message = "Update available",
        )
        val vm = ProwlarrSystemStatusViewModel(repository())
        val state = vm.state.value
        assertEquals("Prowlarr", state.status?.appName)
        assertEquals(listOf("Application Check Update"), state.tasks.map { it.name })
        assertEquals(listOf("Update available"), state.health.map { it.message })
        vm.viewModelScope.coroutineContext.cancelChildren()
    }
}

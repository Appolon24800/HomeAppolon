package dev.appolon.homelab

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.ui.lce.rememberServerConnection
import dev.appolon.homelab.arr.ui.nav.ArrRoute
import dev.appolon.homelab.arr.ui.nav.ArrRouteRegistry
import dev.appolon.homelab.arr.ui.nav.fillPattern
import dev.appolon.homelab.arr.ui.shell.ArrShell
import dev.appolon.homelab.arr.ui.shell.ArrShellContent
import dev.appolon.homelab.arr.ui.shell.LocalArrShellNavigator
import dev.appolon.homelab.arr.ui.shell.ShellServer
import dev.appolon.homelab.arr.ui.shell.ServerSwitcher
import dev.appolon.homelab.arr.ui.theme.ArrTheme
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConnectionRecoveryUiTest {
    @get:Rule val composeRule = createComposeRule()

    private fun awaitText(text: String) {
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(text).assertIsDisplayed()
    }

    @Test
    fun missingConnectionShowsRetryAndThenOpensTheScreen() {
        val attempts = AtomicInteger()
        composeRule.setContent {
            ArrTheme {
                val connection = rememberServerConnection("server") {
                    if (attempts.incrementAndGet() == 1) null else "Connected"
                }
                connection?.let { Text(it) }
            }
        }
        awaitText("Retry")
        composeRule.onNodeWithText("Retry").performClick()
        awaitText("Connected")
        assertEquals(2, attempts.get())
    }

    @Test
    fun thrownSetupFailureShowsRetryAndThenOpensTheScreen() {
        val attempts = AtomicInteger()
        composeRule.setContent {
            ArrTheme {
                val connection = rememberServerConnection("server") {
                    if (attempts.incrementAndGet() == 1) throw IOException("Setup failed")
                    "Connected"
                }
                connection?.let { Text(it) }
            }
        }
        awaitText("Retry")
        composeRule.onNodeWithText("Retry").performClick()
        awaitText("Connected")
    }

    @Test
    fun currentServerRemainsVisibleWhenItsNormalPositionIsInOverflow() {
        val servers = (1..6).map {
            ShellServer(ServerId("server-$it"), "Server $it", AppType.RADARR)
        }
        composeRule.setContent {
            ArrTheme {
                ServerSwitcher(
                    servers = servers,
                    selectedId = servers.last().id,
                    onServerClick = {},
                    onSettingsClick = {},
                )
            }
        }
        awaitText("Server 6")
    }

    @Test
    fun featureNavigationDoesNotEncodeServerIdsAgainAfterReadingArguments() {
        val id = ServerId("server + / %25 ü")
        val registry = ArrRouteRegistry().apply { register("test", "settings/{serverId}") }
        val expected = checkNotNull(fillPattern("settings/{serverId}", mapOf("serverId" to id.value)))
        composeRule.setContent {
            ArrTheme {
                ArrShell(
                    servers = listOf(ShellServer(id, "Special server", AppType.RADARR)),
                    onServerSelected = {},
                    onExit = {},
                    focusServer = id,
                    registry = registry,
                    content = ArrShellContent(
                        serverHome = {
                            val nav = LocalArrShellNavigator.current
                            Button(onClick = { nav.open(ArrRoute.Feature("test", expected)) }) {
                                Text("Open feature")
                            }
                        },
                        feature = { Text(it.path) },
                    ),
                )
            }
        }
        awaitText("Open feature")
        composeRule.onNodeWithText("Open feature").performClick()
        awaitText(expected)
    }

    private class ConnectionModel(val version: Long) : ViewModel()

    @Composable
    private fun ConnectionScreen(label: String, version: Long, next: () -> Unit) {
        val model: ConnectionModel = viewModel { ConnectionModel(version) }
        Column {
            Text("$label connection ${model.version}")
            Button(onClick = next) { Text("Continue") }
        }
    }

    @Test
    fun editingAConnectionRecreatesCurrentAndRetainedScreenViewModels() {
        val id = ServerId("test-server")
        val version = mutableLongStateOf(1L)
        composeRule.setContent {
            ArrTheme {
                ArrShell(
                    servers = listOf(ShellServer(id, "Test", AppType.RADARR, connectionVersion = version.longValue)),
                    onServerSelected = {},
                    onExit = {},
                    focusServer = id,
                    content = ArrShellContent(
                        serverHome = {
                            val nav = LocalArrShellNavigator.current
                            ConnectionScreen("Home", version.longValue) {
                                nav.open(ArrRoute.Library(id, AppType.RADARR))
                            }
                        },
                        library = {
                            val nav = LocalArrShellNavigator.current
                            ConnectionScreen("Library", version.longValue) { nav.back() }
                        },
                    ),
                )
            }
        }
        awaitText("Home connection 1")
        composeRule.onNodeWithText("Continue").performClick()
        awaitText("Library connection 1")
        composeRule.runOnIdle { version.longValue = 2L }
        awaitText("Library connection 2")
        composeRule.onNodeWithText("Continue").performClick()
        awaitText("Home connection 2")
    }
}

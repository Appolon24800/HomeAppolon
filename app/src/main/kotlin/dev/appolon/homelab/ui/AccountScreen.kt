package dev.appolon.homelab.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
/**
 * Native PocketID account management.
 *
 * Sign-in uses PocketID's one-time access codes: generate a code in the
 * PocketID UI (Users > one-time access, or "Alternative sign-in methods" on
 * the login page), type it here, and the app exchanges it for a session that
 * is bridged into the WebView cookie jar — every service's OIDC redirect then
 * authenticates automatically. The status polls the live session, and the
 * shown name is the real PocketID identity.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(vm: HomeViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val baseUrl = state.accountUrl ?: return

    // The account screen is an overlay over the dashboard: system back
    // returns there instead of leaving the app.
    BackHandler(onBack = vm::closeAccount)

    // Live session polling while the screen is visible.
    LaunchedEffect(baseUrl) {
        while (true) {
            vm.refreshPocketIdStatus()
            delay(4_000)
        }
    }

    var code by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Account") },
                navigationIcon = {
                    IconButton(onClick = vm::closeAccount) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("PocketID", style = MaterialTheme.typography.titleLarge)
                    Text(
                        baseUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(16.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = if (state.pocketIdSignedIn) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            when {
                                state.pocketIdSignedIn && !state.pocketIdUser.isNullOrBlank() ->
                                    "Signed in as ${state.pocketIdUser}"
                                state.pocketIdSignedIn -> "Signed in"
                                else -> "Not signed in"
                            },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            if (state.pocketIdSignedIn) {
                                "Services log in automatically through this session"
                            } else {
                                "Sign in once — services then log in automatically"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (state.pocketIdSignedIn) {
                Button(onClick = {
                    vm.signOutPocketId()
                    code = ""
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("Switch account")
                }
                OutlinedButton(onClick = vm::signOutPocketId, modifier = Modifier.fillMaxWidth()) {
                    Text("Sign out")
                }
            } else {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("One-time access code") },
                    supportingText = {
                        Text(
                            "In PocketID: Alternative sign-in methods > one-time code, " +
                                "or ask an admin (Users > one-time access)"
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    singleLine = true,
                    isError = state.pocketIdError != null,
                    modifier = Modifier.fillMaxWidth(),
                )
                state.pocketIdError?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Button(
                    onClick = {
                        vm.signInWithOneTimeCode(code)
                        code = ""
                    },
                    enabled = !state.pocketIdBusy && code.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.pocketIdBusy) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp))
                    } else {
                        Text("Sign in")
                    }
                }
                TextButton(
                    onClick = vm::openPocketIdSignIn,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text("Use the web login page instead")
                }
            }

            var urlDraft by remember(baseUrl) { mutableStateOf(baseUrl) }
            OutlinedTextField(
                value = urlDraft,
                onValueChange = {
                    urlDraft = it
                    vm.setPocketIdBaseUrl(it)
                },
                label = { Text("PocketID URL") },
                supportingText = { Text("Your PocketID server's address") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

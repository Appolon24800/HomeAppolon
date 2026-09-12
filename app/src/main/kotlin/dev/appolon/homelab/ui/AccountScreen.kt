package dev.appolon.homelab.ui

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.appolon.homelab.data.PocketId
import kotlinx.coroutines.delay

/**
 * Native PocketID account management. Signing in opens the PocketID UI in the
 * in-app WebView; because the WebView cookie jar is app-global, the session
 * then answers every service's OIDC redirect automatically. The status here
 * polls the cookie jar, and a completed sign-in auto-closes the login page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(vm: HomeViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val baseUrl = state.accountUrl ?: return

    // The account screen is an overlay over the dashboard: system back
    // returns there instead of leaving the app.
    androidx.activity.compose.BackHandler(onBack = vm::closeAccount)

    // Live status + auto-close of the sign-in page once the session exists.
    LaunchedEffect(baseUrl) {
        while (true) {
            vm.refreshPocketIdStatus()
            val t = state.webTarget
            if (t?.pocketIdSignIn == true && PocketId.hasSession(baseUrl)) {
                vm.closeWeb()
            }
            delay(1_000)
        }
    }

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
                            if (state.pocketIdSignedIn) "Signed in" else "Not signed in",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (state.pocketIdSignedIn && state.pocketIdAccount.isNotBlank()) {
                            Text(
                                state.pocketIdAccount,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Text(
                                "Services log in automatically through this session",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            if (state.pocketIdSignedIn) {
                Button(onClick = vm::openPocketIdSignIn, modifier = Modifier.fillMaxWidth()) {
                    Text("Switch account")
                }
                OutlinedButton(onClick = vm::signOutPocketId, modifier = Modifier.fillMaxWidth()) {
                    Text("Sign out")
                }
            } else {
                Button(onClick = vm::openPocketIdSignIn, modifier = Modifier.fillMaxWidth()) {
                    Text("Sign in")
                }
            }

            val browserContext = androidx.compose.ui.platform.LocalContext.current
            TextButton(
                onClick = {
                    Urls.open(browserContext, baseUrl)
                },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text("Open PocketID in browser instead")
            }

            OutlinedTextField(
                value = state.pocketIdAccount,
                onValueChange = vm::savePocketIdAccountLabel,
                label = { Text("Account label (optional)") },
                supportingText = { Text("A name to recognise this account in the app") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

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

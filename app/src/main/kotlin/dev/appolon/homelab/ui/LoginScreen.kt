package dev.appolon.homelab.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.appolon.homelab.data.PocketId
import dev.appolon.homelab.ui.components.ErrorText
import kotlinx.coroutines.launch

/** Auth methods all shown at once; no mode switching. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(vm: HomeViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    BackHandler(onBack = vm::closeLogin)

    var code by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var emailCode by remember { mutableStateOf("") }
    var passkeyBusy by remember { mutableStateOf(false) }
    var passkeyError by remember { mutableStateOf<String?>(null) }

    fun startPasskey() {
        val activity = context as? android.app.Activity
        if (activity == null) {
            passkeyError = "Passkeys need an activity context"
            return
        }
        scope.launch {
            passkeyBusy = true
            passkeyError = null
            runCatching {
                val challenge = PocketId.beginPasskeyLogin(state.accountUrl)
                val manager = CredentialManager.create(activity)
                val response = manager.getCredential(
                    activity,
                    GetCredentialRequest(listOf(GetPublicKeyCredentialOption(challenge.requestJson))),
                )
                val credential = response.credential as? PublicKeyCredential
                    ?: error("The authenticator did not return a passkey")
                vm.finishPasskeyLogin(challenge, credential.authenticationResponseJson)
            }.onFailure { e ->
                // The user backed out of the system dialog — not an error.
                if (e !is GetCredentialCancellationException) {
                    passkeyError = e.message ?: "Passkey sign-in failed"
                }
            }
            passkeyBusy = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sign in") },
                navigationIcon = {
                    IconButton(onClick = vm::closeLogin) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(12.dp))
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = null,
                    modifier = Modifier.padding(20.dp).size(32.dp),
                )
            }
            Text("PocketID", style = MaterialTheme.typography.headlineSmall)

            state.deviceLogin?.let { device ->
                DeviceLoginCard(
                    device = device,
                    onCancel = vm::cancelDeviceLogin,
                )
            }

            Button(
                onClick = ::startPasskey,
                enabled = !passkeyBusy && !state.accountBusy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (passkeyBusy) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                } else {
                    Text("Sign in with a passkey")
                }
            }
            ErrorText(passkeyError)
            OutlinedButton(
                onClick = vm::startDeviceLogin,
                enabled = state.deviceLogin == null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Sign in with other device")
            }

            OutlinedTextField(
                value = code,
                onValueChange = { code = it },
                label = { Text("Login code") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                singleLine = true,
                isError = state.accountError != null,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email login") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true,
                isError = state.accountError != null,
                modifier = Modifier.fillMaxWidth(),
            )
            ErrorText(state.accountError)
            Button(
                onClick = { vm.requestOneTimeCodeEmail(email) },
                enabled = !state.accountBusy && email.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.accountBusy && state.codeSentTo == null) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                } else {
                    Text(if (state.codeSentTo != null) "Send a new code" else "Send the code")
                }
            }
            if (state.codeSentTo != null) {
                OutlinedTextField(
                    value = emailCode,
                    onValueChange = { emailCode = it },
                    label = { Text("Emailed code") },
                    supportingText = { Text("Sent to ${state.codeSentTo}") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    singleLine = true,
                    isError = state.accountError != null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Button(
                onClick = {
                    vm.signInWithOneTimeCode(if (emailCode.isNotBlank()) emailCode else code)
                    code = ""
                    emailCode = ""
                },
                enabled = !state.accountBusy && (code.isNotBlank() || emailCode.isNotBlank()),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.accountBusy && state.codeSentTo != null) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                } else {
                    Text("Sign in")
                }
            }

            TextButton(onClick = vm::openWebLogin) {
                Text(state.accountUrl.removePrefix("https://"))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Big code + QR + "waiting" state for the other-device sign-in. */
@Composable
private fun DeviceLoginCard(device: PocketId.DeviceLoginRequest, onCancel: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(20.dp),
        ) {
            Text("On your other device, open", style = MaterialTheme.typography.bodyMedium)
            Text(
                device.verificationUri.removePrefix("https://"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            QrCode(content = device.verificationUriComplete, size = 220.dp)
            Text(
                device.userCode.let { if (it.length > 4) "${it.take(4)} - ${it.drop(4)}" else it },
                style = MaterialTheme.typography.headlineMedium.copy(letterSpacing = 4.sp),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(
                    "Waiting for approval…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onCancel) {
                Text("Cancel")
            }
        }
    }
}

private const val QR_PIXELS = 512

/** Renders [content] as a QR code; encoding runs off the main thread. */
@Composable
private fun QrCode(content: String, size: androidx.compose.ui.unit.Dp) {
    val qr by androidx.compose.runtime.produceState<ImageBitmap?>(initialValue = null, content) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            runCatching {
                val bits = com.google.zxing.qrcode.QRCodeWriter()
                    .encode(content, com.google.zxing.BarcodeFormat.QR_CODE, QR_PIXELS, QR_PIXELS)
                val colors = IntArray(QR_PIXELS * QR_PIXELS) { i ->
                    if (bits[i % QR_PIXELS, i / QR_PIXELS]) android.graphics.Color.BLACK
                    else android.graphics.Color.WHITE
                }
                android.graphics.Bitmap.createBitmap(
                    colors, QR_PIXELS, QR_PIXELS, android.graphics.Bitmap.Config.ARGB_8888,
                ).asImageBitmap()
            }.getOrNull()
        }
    }
    qr?.let {
        Image(
            bitmap = it,
            contentDescription = "Device login QR code",
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(size),
        )
    }
}

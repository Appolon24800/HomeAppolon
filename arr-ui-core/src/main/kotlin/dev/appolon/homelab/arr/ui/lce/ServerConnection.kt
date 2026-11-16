package dev.appolon.homelab.arr.ui.lce

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.appolon.homelab.arr.core.ArrError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Returns connected clients, or draws loading/error UI with a retry action. */
@Composable
fun <T : Any> rememberServerConnection(
    vararg keys: Any?,
    modifier: Modifier = Modifier,
    connect: suspend () -> T?,
): T? {
    var state by remember(*keys) { mutableStateOf<LceState<T>>(LceState.Loading) }
    var attempt by remember(*keys) { mutableIntStateOf(0) }
    val currentConnect by rememberUpdatedState(connect)
    LaunchedEffect(*keys, attempt) {
        state = LceState.Loading
        state = try {
            withContext(Dispatchers.IO) { currentConnect() }?.let { LceState.Content(it) }
                ?: LceState.Error(ArrError.Unknown(null))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LceState.Error(ArrError.Unknown(e))
        }
    }
    val connected = (state as? LceState.Content)?.data
    if (connected == null) {
        ArrLceBox(state = state, onRetry = { attempt++ }, modifier = modifier) {}
    }
    return connected
}

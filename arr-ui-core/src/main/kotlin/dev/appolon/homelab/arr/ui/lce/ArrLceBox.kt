package dev.appolon.homelab.arr.ui.lce

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.ui.R
import dev.appolon.homelab.arr.ui.components.EmptyState

/**
 * Renders one [LceState]: centered spinner for [LceState.Loading], an error
 * pane with retry for [LceState.Error], [EmptyState] for [LceState.Empty],
 * and [content] for [LceState.Content]. Screens never crash on IO; failures
 * land here with a user-presentable message derived from [ArrError].
 */
@Composable
fun <T> ArrLceBox(
    state: LceState<T>,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    emptyTitle: String = stringResource(R.string.arr_empty_title),
    emptyMessage: String = stringResource(R.string.arr_empty_default_message),
    content: @Composable BoxScope.(T) -> Unit,
) {
    when (state) {
        LceState.Loading -> Box(
            contentAlignment = Alignment.Center,
            modifier = modifier.fillMaxSize(),
        ) {
            CircularProgressIndicator()
        }

        LceState.Empty -> EmptyState(
            title = emptyTitle,
            message = emptyMessage,
            modifier = modifier.fillMaxSize(),
        )

        is LceState.Error -> ArrErrorPane(error = state.error, onRetry = onRetry, modifier = modifier)

        is LceState.Content -> Box(modifier = modifier) { content(state.data) }
    }
}

/** Same layout as the dashboard's ErrorState: icon, title, detail, retry. */
@Composable
private fun ArrErrorPane(error: ArrError, onRetry: (() -> Unit)?, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
    ) {
        Spacer(Modifier.weight(1f))
        Icon(
            imageVector = Icons.Outlined.CloudOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.size(12.dp))
        Text(
            text = stringResource(R.string.arr_error_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.size(4.dp))
        Text(
            text = arrErrorMessage(error),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (onRetry != null) {
            Spacer(Modifier.size(20.dp))
            Button(onClick = onRetry) {
                Text(stringResource(R.string.arr_retry))
            }
        }
        Spacer(Modifier.weight(1f))
    }
}

/** User-presentable message for any [ArrError], resolved from string resources. */
@Composable
fun arrErrorMessage(error: ArrError): String = when (error) {
    is ArrError.Network -> stringResource(R.string.arr_error_network)
    is ArrError.Http -> stringResource(R.string.arr_error_http, error.code)
    is ArrError.Auth -> stringResource(R.string.arr_error_auth, error.code)
    is ArrError.Parse -> stringResource(R.string.arr_error_parse)
    is ArrError.Offline -> stringResource(R.string.arr_error_offline)
    is ArrError.Conflicted -> stringResource(R.string.arr_error_conflict, error.code)
    is ArrError.Unknown -> stringResource(R.string.arr_error_unknown)
}

/** Resource-id half of [arrErrorMessage]; pure so tests can verify the mapping. */
internal fun arrErrorMessageId(error: ArrError): Int = when (error) {
    is ArrError.Network -> R.string.arr_error_network
    is ArrError.Http -> R.string.arr_error_http
    is ArrError.Auth -> R.string.arr_error_auth
    is ArrError.Parse -> R.string.arr_error_parse
    is ArrError.Offline -> R.string.arr_error_offline
    is ArrError.Conflicted -> R.string.arr_error_conflict
    is ArrError.Unknown -> R.string.arr_error_unknown
}

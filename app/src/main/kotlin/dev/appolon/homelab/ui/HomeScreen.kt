package dev.appolon.homelab.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.appolon.homelab.data.HomeUiState
import dev.appolon.homelab.data.Search
import dev.appolon.homelab.ui.components.ErrorState
import dev.appolon.homelab.ui.components.GroupHeader
import dev.appolon.homelab.ui.components.LoadingState
import dev.appolon.homelab.ui.components.MessageBanner
import dev.appolon.homelab.ui.components.NoResults
import dev.appolon.homelab.ui.components.OfflineIndicator
import dev.appolon.homelab.ui.components.SearchTopBar
import dev.appolon.homelab.ui.components.ServiceRow

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(vm: HomeViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scrollBehavior = remember { TopAppBarDefaults.enterAlwaysScrollBehavior() }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> vm.onForeground()
                Lifecycle.Event.ON_STOP -> vm.onBackground()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            if (state.searching) {
                SearchTopBar(
                    query = state.query,
                    onQueryChange = vm::onQueryChange,
                    onClose = { vm.setSearching(false) },
                )
            } else {
                HomeTopBar(
                    state = state,
                    scrollBehavior = scrollBehavior,
                    onSearch = { vm.setSearching(true) },
                    onLink = { Urls.open(context, it) },
                )
            }
        },
    ) { padding ->
        val config = state.config
        when {
            config == null && state.loading -> LoadingState()
            config == null -> ErrorState(onRetry = { vm.load() })
            else -> ServiceList(
                state = state,
                contentPadding = padding,
                onRefresh = { vm.load(showSpinner = true) },
                onServiceClick = { Urls.open(context, it) },
                onDismissMessage = vm::dismissMessage,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTopBar(
    state: HomeUiState,
    scrollBehavior: TopAppBarScrollBehavior,
    onSearch: () -> Unit,
    onLink: (String) -> Unit,
) {
    LargeTopAppBar(
        title = {
            Column {
                Text(
                    text = state.config?.title ?: "Homelab",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                state.config?.subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        actions = {
            IconButton(onClick = onSearch) {
                Icon(Icons.Filled.Search, contentDescription = "Search")
            }
            var menuOpen by remember { mutableStateOf(false) }
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Links")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                state.config?.links.orEmpty().forEach { link ->
                    DropdownMenuItem(
                        text = { Text(link.name) },
                        onClick = {
                            menuOpen = false
                            onLink(link.url)
                        },
                    )
                }
            }
        },
        scrollBehavior = scrollBehavior,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ServiceList(
    state: HomeUiState,
    contentPadding: PaddingValues,
    onRefresh: () -> Unit,
    onServiceClick: (String) -> Unit,
    onDismissMessage: () -> Unit,
) {
    val config = state.config ?: return
    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = onRefresh,
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            if (state.query.isEmpty()) {
                val info = state.message
                if (info != null && !state.messageDismissed) {
                    item(key = "message") { MessageBanner(info, onDismissMessage) }
                }
                if (state.offline) {
                    item(key = "offline") { OfflineIndicator() }
                }
            }
            val sections = Search.filterGroups(config.services, state.query)
            sections.forEach { (group, items) ->
                stickyHeader(key = "header_${group.name}") { GroupHeader(group.name) }
                items(items, key = { "${group.name}::${it.name}" }) { item ->
                    ServiceRow(item) { onServiceClick(item.url) }
                }
            }
            if (sections.isEmpty() && state.query.isNotBlank()) {
                item(key = "no_results") { NoResults(state.query) }
            }
        }
    }
}

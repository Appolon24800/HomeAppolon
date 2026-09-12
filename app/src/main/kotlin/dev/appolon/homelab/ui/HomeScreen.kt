package dev.appolon.homelab.ui

import android.app.Activity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.appolon.homelab.data.HomeUiState
import dev.appolon.homelab.data.Search
import dev.appolon.homelab.ui.AccountScreen
import dev.appolon.homelab.ui.components.ErrorState
import dev.appolon.homelab.ui.components.GroupHeader
import dev.appolon.homelab.ui.components.LoadingState
import dev.appolon.homelab.ui.components.MessageBanner
import dev.appolon.homelab.ui.components.NoResults
import dev.appolon.homelab.ui.components.OfflineIndicator
import dev.appolon.homelab.ui.components.SearchTopBar
import dev.appolon.homelab.ui.components.ServiceRow
import dev.appolon.homelab.ui.theme.HomelabTheme
import dev.appolon.homelab.ui.theme.ThemeSource
import dev.appolon.homelab.ui.theme.contrastOn
import dev.appolon.homelab.ui.theme.homerHeaderTextColor
import dev.appolon.homelab.ui.theme.parseHexColor

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(externalUrl: String? = null, vm: HomeViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Deep links (home.appolon.dev/...) open straight into the WebView.
    LaunchedEffect(externalUrl) {
        val root = dev.appolon.homelab.data.Homer.BASE_URL.trimEnd('/')
        if (!externalUrl.isNullOrBlank() && externalUrl.trimEnd('/') != root) {
            vm.openWeb(externalUrl, null)
        }
    }

    // Status-bar icon contrast: the Homer top bar is a coloured (red/blue)
    // header, so light icons are needed regardless of system dark mode.
    val homerBarActive = state.themeSource == ThemeSource.HOMER && state.config?.colors != null
    DisposableEffect(homerBarActive) {
        val window = (context as? Activity)?.window
        if (window != null) {
            WindowCompat.getInsetsController(window, window.decorView)
                .isAppearanceLightStatusBars = !homerBarActive
        }
        onDispose {}
    }

    HomelabTheme(themeSource = state.themeSource, homerColors = state.config?.colors) {
        // Overlays stack: dashboard < account screen < in-app browser. The
        // list stays composed underneath so its scroll position survives.
        Box(Modifier.fillMaxSize()) {
            HomeScreenBody(state = state, vm = vm, homerBarActive = homerBarActive)
            if (state.accountUrl != null) {
                AccountScreen(vm)
            }
            state.webTarget?.let { target ->
                WebScreen(target = target, onClose = vm::closeWeb)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun HomeScreenBody(state: HomeUiState, vm: HomeViewModel, homerBarActive: Boolean) {
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

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
                    homerBarActive = homerBarActive,
                    scrollBehavior = scrollBehavior,
                    onSearch = { vm.setSearching(true) },
                    onLink = { url, name -> vm.openWeb(url, name) },
                    onToggleTheme = vm::toggleTheme,
                    onOpenAccount = vm::openAccount,
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
                onServiceClick = { url, name -> vm.openWeb(url, name) },
                onDismissMessage = vm::dismissMessage,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTopBar(
    state: HomeUiState,
    homerBarActive: Boolean,
    scrollBehavior: TopAppBarScrollBehavior,
    onSearch: () -> Unit,
    onLink: (String, String?) -> Unit,
    onToggleTheme: () -> Unit,
    onOpenAccount: () -> Unit,
) {
    val homerColors = if (homerBarActive) state.config?.colors else null
    val barColors: TopAppBarColors = if (homerColors != null) {
        val active = if (isSystemInDarkTheme()) homerColors.dark else homerColors.light
        val header = homerHeaderTextColor(active)
        val primary = parseHexColor(active.highlightPrimary) ?: MaterialTheme.colorScheme.primary
        TopAppBarDefaults.topAppBarColors(
            containerColor = primary,
            titleContentColor = header,
            navigationIconContentColor = header,
            actionIconContentColor = header,
        )
    } else {
        TopAppBarDefaults.topAppBarColors()
    }

    TopAppBar(
        title = {
            Column {
                Text(
                    text = state.config?.title ?: "Homelab",
                    style = MaterialTheme.typography.titleLarge,
                )
                state.config?.subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = barColors.titleContentColor.copy(alpha = 0.8f),
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
                DropdownMenuItem(
                    text = { Text("PocketID account") },
                    onClick = {
                        menuOpen = false
                        onOpenAccount()
                    },
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            if (state.themeSource == ThemeSource.HOMER) "Use phone theme"
                            else "Use Homer theme"
                        )
                    },
                    onClick = {
                        menuOpen = false
                        onToggleTheme()
                    },
                )
                state.config?.links.orEmpty().forEach { link ->
                    DropdownMenuItem(
                        text = { Text(link.name) },
                        onClick = {
                            menuOpen = false
                            onLink(link.url, link.name)
                        },
                    )
                }
            }
        },
        colors = barColors,
        scrollBehavior = scrollBehavior,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun ServiceList(
    state: HomeUiState,
    contentPadding: PaddingValues,
    onRefresh: () -> Unit,
    onServiceClick: (String, String?) -> Unit,
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
            contentPadding = PaddingValues(bottom = 12.dp),
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
                    ServiceRow(item) { onServiceClick(item.url, item.name) }
                }
            }
            if (sections.isEmpty() && state.query.isNotBlank()) {
                item(key = "no_results") { NoResults(state.query) }
            }
        }
    }
}

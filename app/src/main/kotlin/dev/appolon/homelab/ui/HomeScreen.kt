package dev.appolon.homelab.ui

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
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
import dev.appolon.homelab.data.Favorites
import dev.appolon.homelab.data.ServiceItem
import dev.appolon.homelab.data.PocketId
import dev.appolon.homelab.data.WebTarget
import dev.appolon.homelab.data.Search
import dev.appolon.homelab.ui.components.AccountAvatar
import dev.appolon.homelab.ui.components.AccountAvatarTinted
import dev.appolon.homelab.ui.components.ErrorState
import dev.appolon.homelab.ui.components.GroupHeader
import dev.appolon.homelab.ui.components.LoadingState
import dev.appolon.homelab.ui.components.MessageBanner
import dev.appolon.homelab.ui.components.NoResults
import dev.appolon.homelab.ui.components.OfflineIndicator
import dev.appolon.homelab.ui.components.SearchTopBar
import dev.appolon.homelab.ui.components.ServiceRow
import dev.appolon.homelab.ui.components.homeTopBarInsets
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

    // Overlay transitions: forward in, back out — consistent durations.
    val enterMillis = 300
    val exitMillis = 240

    HomelabTheme(themeSource = state.themeSource, homerColors = state.config?.colors) {
        // Overlays stack: dashboard < account sheet < sign-in < in-app browser.
        // The list stays composed underneath so its scroll position survives.
        // Full-screen overlays animate: pages slide horizontally (forward in
        // from the right, back out to the right), the sign-in sheet slides up.
        Box(Modifier.fillMaxSize()) {
            HomeScreenBody(state = state, vm = vm, homerBarActive = homerBarActive)
            if (state.accountSheetOpen) {
                AccountSheet(state = state, vm = vm)
            }

            var lastLogin by remember { mutableStateOf(false) }
            androidx.compose.runtime.SideEffect {
                if (state.loginOpen) lastLogin = true
            }
            AnimatedVisibility(
                visible = state.loginOpen,
                enter = slideInVertically(tween(enterMillis)) { it } + fadeIn(tween(enterMillis)),
                exit = slideOutVertically(tween(exitMillis)) { it } + fadeOut(tween(exitMillis)),
            ) {
                if (lastLogin) LoginScreen(vm = vm)
            }

            // Remember the last target so the exit animation still has a page
            // to slide out once closeWeb() nulls the state.
            var lastWebTarget by remember { mutableStateOf<WebTarget?>(null) }
            androidx.compose.runtime.SideEffect {
                state.webTarget?.let { lastWebTarget = it }
            }
            AnimatedVisibility(
                visible = state.webTarget != null,
                enter = slideInHorizontally(tween(enterMillis)) { it } + fadeIn(tween(enterMillis)),
                exit = slideOutHorizontally(tween(exitMillis)) { it } + fadeOut(tween(exitMillis)),
            ) {
                lastWebTarget?.let { target ->
                    // Web fallback login: once a session cookie lands in the
                    // jar, adopt it as the active account and close the page.
                    if (target.webLogin) {
                        LaunchedEffect(target) {
                            while (true) {
                                if (PocketId.hasSession(state.accountUrl.ifBlank { PocketId.WELL_KNOWN_BASE_URL })) {
                                    vm.adoptWebLoginSession()
                                    kotlinx.coroutines.delay(800)
                                    vm.closeWeb()
                                    break
                                }
                                kotlinx.coroutines.delay(1_000)
                            }
                        }
                    }
                    WebScreen(
                        target = target,
                        onClose = vm::closeWeb,
                        authBaseUrl = state.accountUrl.ifBlank {
                            PocketId.WELL_KNOWN_BASE_URL
                        },
                    )
                }
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
                    onOpenAccount = vm::openAccountSheet,
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
                onToggleFavorite = vm::toggleFavorite,
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
    val accountLabel = state.activeAccount?.label
        ?: state.accountUser?.let { it.displayName ?: it.username }
    val avatarUrl = state.activeAccount?.let { PocketId.accountAvatarUrl(state.accountUrl, it.id) }
        ?: state.accountUser?.let { PocketId.accountAvatarUrl(state.accountUrl, it.id) }
    val darkTheme = isSystemInDarkTheme()
    // Parse the Homer palette once per theme flip; only plain functions run
    // inside remember (TopAppBarDefaults.topAppBarColors is composable).
    val defaultBarColors = TopAppBarDefaults.topAppBarColors()
    val fallbackPrimary = MaterialTheme.colorScheme.primary
    val homerBarPair = remember(homerColors, darkTheme) {
        homerColors?.let {
            val active = if (darkTheme) it.dark else it.light
            homerHeaderTextColor(active) to parseHexColor(active.highlightPrimary)
        }
    }
    val barColors: TopAppBarColors = if (homerBarPair != null) {
        val (header, primary) = homerBarPair
        TopAppBarDefaults.topAppBarColors(
            containerColor = primary ?: fallbackPrimary,
            titleContentColor = header,
            navigationIconContentColor = header,
            actionIconContentColor = header,
        )
    } else {
        defaultBarColors
    }

    val title = stringResource(dev.appolon.homelab.R.string.app_name)
    BoxWithConstraints {
        val insets = homeTopBarInsets(maxWidth, title)
        TopAppBar(
            title = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            actions = {
                IconButton(onClick = onSearch) {
                    Icon(Icons.Filled.Search, contentDescription = "Search")
                }
                IconButton(onClick = onOpenAccount) {
                    if (homerColors != null) {
                        val header = homerHeaderTextColor(if (darkTheme) homerColors.dark else homerColors.light)
                        AccountAvatarTinted(
                            label = accountLabel,
                            foreground = header,
                            avatarUrl = avatarUrl,
                            size = 32.dp,
                        )
                    } else {
                        AccountAvatar(label = accountLabel, avatarUrl = avatarUrl, size = 32.dp)
                    }
                }
                var menuOpen by remember { mutableStateOf(false) }
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Links")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
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
            windowInsets = insets,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun ServiceList(
    state: HomeUiState,
    contentPadding: PaddingValues,
    onRefresh: () -> Unit,
    onServiceClick: (String, String?) -> Unit,
    onDismissMessage: () -> Unit,
    onToggleFavorite: (ServiceItem) -> Unit = {},
) {
    val config = state.config ?: return
    // Filtered outside LazyColumn: its content block has no composable scope,
    // and this way one recomposition computes sections once.
    val sections = remember(config, state.query) { Search.filterGroups(config.services, state.query) }
    val favorites = remember(config, state.favorites, state.query) {
        Favorites.items(config.services, state.favorites, state.query)
    }
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
            if (favorites.isNotEmpty()) {
                stickyHeader(key = "favorites_header", contentType = "group_header") {
                    GroupHeader("Favorites")
                }
                items(favorites, key = { "favorite::${Favorites.id(it)}" }, contentType = { "service" }) { item ->
                    ServiceRow(
                        item = item,
                        favorite = true,
                        onToggleFavorite = { onToggleFavorite(item) },
                        onClick = { onServiceClick(item.url, item.name) },
                    )
                }
            }
            sections.forEach { (group, items) ->
                stickyHeader(
                    key = "header_${group.name}",
                    contentType = "group_header",
                ) { GroupHeader(group.name) }
                items(
                    items,
                    key = { "${group.name}::${it.name}" },
                    contentType = { "service" },
                ) { item ->
                    ServiceRow(
                        item = item,
                        favorite = Favorites.id(item) in state.favorites,
                        onToggleFavorite = { onToggleFavorite(item) },
                        onClick = { onServiceClick(item.url, item.name) },
                    )
                }
            }
            if (sections.isEmpty() && state.query.isNotBlank()) {
                item(key = "no_results") { NoResults(state.query) }
            }
        }
    }
}

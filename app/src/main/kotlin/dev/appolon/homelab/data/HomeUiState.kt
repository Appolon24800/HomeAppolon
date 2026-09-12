package dev.appolon.homelab.data

import dev.appolon.homelab.ui.theme.ThemeSource

/** A page opened inside the app's WebView. */
data class WebTarget(
    val url: String,
    val initialTitle: String? = null,
)

/** Immutable snapshot of everything the home screen renders. */
data class HomeUiState(
    val config: HomerConfig? = null,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val searching: Boolean = false,
    val query: String = "",
    val message: MessageInfo? = null,
    val messageDismissed: Boolean = false,
    /** True when the fresh fetch failed but a cached config is being shown. */
    val offline: Boolean = false,
    /** Which palette drives the app; persisted across launches. */
    val themeSource: ThemeSource = ThemeSource.HOMER,
    /** Non-null while a service page is open in the in-app WebView. */
    val webTarget: WebTarget? = null,
)

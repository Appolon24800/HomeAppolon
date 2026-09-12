package dev.appolon.homelab.data

import dev.appolon.homelab.ui.theme.ThemeSource

/** A page opened inside the app's WebView. */
data class WebTarget(
    val url: String,
    val initialTitle: String? = null,
    /** Auto-close the page once a PocketID session cookie appears. */
    val pocketIdSignIn: Boolean = false,
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
    /** Non-null while the PocketID account screen is shown. */
    val accountUrl: String? = null,
    val pocketIdSignedIn: Boolean = false,
    /** Real display name from the PocketID API when a session is live. */
    val pocketIdUser: String? = null,
    val pocketIdAccount: String = "",
    val pocketIdBusy: Boolean = false,
    val pocketIdError: String? = null,
)

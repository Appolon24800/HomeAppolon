package dev.appolon.homelab.data

import dev.appolon.homelab.ui.theme.ThemeSource

/** A page opened inside the app's WebView. */
data class WebTarget(
    val url: String,
    /** Auto-close the page once a PocketID session cookie appears. */
    val webLogin: Boolean = false,
)

/** Immutable snapshot of everything the home screen renders. */
@androidx.compose.runtime.Immutable
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
    /** PocketID base URL resolved at startup (stored or default). */
    val accountUrl: String = "",
    /** Saved PocketID accounts for the switcher. */
    val accounts: List<PocketIdAccount> = emptyList(),
    /** The account whose session is live in the cookie jar, if any. */
    val activeAccountId: String? = null,
    /** Live identity from the PocketID API; null when the session is gone. */
    val accountUser: PocketId.PocketIdUser? = null,
    /** True while the account switcher sheet is open. */
    val accountSheetOpen: Boolean = false,
    /** True while the native sign-in screen is open. */
    val loginOpen: Boolean = false,
    /** Sign-in/switching in flight. */
    val accountBusy: Boolean = false,
    val accountError: String? = null,
    /** Email a one-time code was just sent to; drives the login hint. */
    val codeSentTo: String? = null,
    /** Active device-login ("other device") request, shown as code + QR. */
    val deviceLogin: PocketId.DeviceLoginRequest? = null,
) {
    val activeAccount: PocketIdAccount? get() = accounts.firstOrNull { it.id == activeAccountId }
}

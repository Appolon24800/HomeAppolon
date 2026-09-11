package dev.appolon.homelab.data

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
)

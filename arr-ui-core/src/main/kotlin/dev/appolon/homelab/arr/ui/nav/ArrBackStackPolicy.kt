package dev.appolon.homelab.arr.ui.nav

import dev.appolon.homelab.arr.core.ServerId

/** What a system back press should do inside the arr shell. */
sealed interface ArrBackDirective {
    /** Pop the navigation stack normally; a parent screen is underneath. */
    data object PopStack : ArrBackDirective

    /**
     * The stack is at its root. Hand control back to the dashboard through the
     * shell's exit callback (the arr shell never finishes the activity itself).
     */
    data object ExitToDashboard : ArrBackDirective
}

/**
 * Back policy for the whole shell: while the NavHost can pop, pop; at the root
 * (the server list) leave the shell. Kept pure so the behavior is unit-testable.
 */
fun arrBackDirective(canPop: Boolean): ArrBackDirective =
    if (canPop) ArrBackDirective.PopStack else ArrBackDirective.ExitToDashboard

/** What tapping a server in the switcher should do with the back stack. */
sealed interface ServerSwitchDirective {
    /** The target server is already on top; navigating again would duplicate it. */
    data object None : ServerSwitchDirective

    /**
     * Pop every server-scoped stack back to the server list (keeping each stack's
     * state) and open the target server's home.
     */
    data class SwitchTo(
        val popToRoot: Boolean,
        val saveState: Boolean,
        val restoreState: Boolean,
    ) : ServerSwitchDirective
}

/**
 * Server switch policy. Switching servers must not stack one server's screens on
 * top of another's: the previous stack is popped to the root and saved, and the
 * target stack is restored when it exists.
 */
fun serverSwitchDirective(current: ServerId?, target: ServerId): ServerSwitchDirective =
    if (current == target) {
        ServerSwitchDirective.None
    } else {
        ServerSwitchDirective.SwitchTo(popToRoot = true, saveState = true, restoreState = true)
    }

package dev.appolon.homelab.arr.ui.shell

import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.ServerId

/**
 * Server descriptor the shell renders in its switcher. Deliberately free of any
 * :arr-data types so the shell stays renderable in previews and UI tests; the
 * integration layer maps its registry rows onto this class.
 */
data class ShellServer(
    val id: ServerId,
    val name: String,
    val appType: AppType,
    val healthy: Boolean = true,
)

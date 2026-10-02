package dev.appolon.homelab.data

/** Favorites belong to this device and survive config renames and regrouping. */
object Favorites {
    fun id(item: ServiceItem): String = item.url.trim()

    fun toggle(saved: Set<String>, id: String): Set<String> = when {
        id.isBlank() -> saved
        id in saved -> saved - id
        else -> saved + id
    }

    fun items(groups: List<ServiceGroup>, saved: Set<String>, query: String): List<ServiceItem> =
        groups.flatMap { it.items }
            .filter { id(it) in saved && Search.matches(it, query) }
            .distinctBy(::id)
}

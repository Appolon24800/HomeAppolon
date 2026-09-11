package dev.appolon.homelab.data

import java.text.Normalizer

/**
 * In-memory, case- and diacritic-insensitive service filtering.
 * A query matches when every whitespace-separated token is found in the
 * service's name, subtitle or keywords.
 */
object Search {

    fun normalize(value: String): String =
        Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")

    fun matches(item: ServiceItem, query: String): Boolean {
        val tokens = normalize(query).split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return true
        val haystack = normalize(
            listOfNotNull(item.name, item.subtitle, item.keywords).joinToString(" "),
        )
        return tokens.all { haystack.contains(it) }
    }

    /** Groups narrowed to their matching items; groups with no match are dropped. */
    fun filterGroups(
        groups: List<ServiceGroup>,
        query: String,
    ): List<Pair<ServiceGroup, List<ServiceItem>>> =
        groups.mapNotNull { group ->
            val items = group.items.filter { matches(it, query) }
            if (items.isEmpty()) null else group to items
        }
}

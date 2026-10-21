package dev.appolon.homelab.arr.core

/** Filter control types a library screen can render. Labels belong to the caller. */
sealed interface FilterSpec {
    val id: String

    /** Pick from a fixed set of option ids, single or multi select. */
    data class Choice(override val id: String, val options: List<String>) : FilterSpec {
        init {
            require(options.distinct().size == options.size) { "duplicate option in filter $id" }
        }
    }

    /** Inclusive numeric range between [min] and [max]. */
    data class Range(override val id: String, val min: Double, val max: Double) : FilterSpec {
        init {
            require(min <= max) { "min $min > max $max for filter $id" }
        }
    }

    /** Tri-state switch: on, off, or unset. */
    data class Toggle(override val id: String) : FilterSpec
}

/** Current value of one filter. The empty form of each type counts as inactive. */
sealed interface FilterValue {
    /** Selected option ids; empty means the filter narrows nothing. */
    data class Choice(val selected: Set<String>) : FilterValue

    /** Bounds in force; null on either side leaves that side open. */
    data class Range(val min: Double?, val max: Double?) : FilterValue

    /** null means unset, true and false are both active states. */
    data class Toggle(val on: Boolean?) : FilterValue
}

/** Immutable set of filter values keyed by [FilterSpec.id]. */
data class FilterState(val values: Map<String, FilterValue> = emptyMap()) {

    fun value(id: String): FilterValue? = values[id]

    fun with(id: String, value: FilterValue): FilterState = copy(values = values + (id to value))

    fun without(id: String): FilterState = copy(values = values - id)

    fun clear(): FilterState = FilterState()

    /** True when the stored value actually narrows results. */
    fun isActive(id: String): Boolean = when (val v = values[id]) {
        null -> false
        is FilterValue.Choice -> v.selected.isNotEmpty()
        is FilterValue.Range -> v.min != null || v.max != null
        is FilterValue.Toggle -> v.on != null
    }
}

/** Binds a [FilterSpec] to its matching logic over an arbitrary item type. */
data class FilterDefinition<T>(
    val spec: FilterSpec,
    val predicate: (T, FilterValue) -> Boolean,
)

/**
 * Pure filter application: keeps items where every active filter's predicate
 * passes. Filters AND across each other, options OR inside a choice selection.
 * Inactive filters and state entries without a definition are ignored, and the
 * input list is left untouched. Throws [IllegalArgumentException] when two
 * definitions share a filter id or a value's type does not match its spec.
 */
fun <T> applyFilters(
    items: List<T>,
    definitions: List<FilterDefinition<T>>,
    state: FilterState,
): List<T> {
    require(definitions.distinctBy { it.spec.id }.size == definitions.size) {
        "duplicate filter id in definitions"
    }
    val active = definitions.filter { state.isActive(it.spec.id) }
    if (active.isEmpty()) return items
    return items.filter { item ->
        active.all { definition ->
            val value = checkNotNull(state.values[definition.spec.id])
            require(valueMatches(definition.spec, value)) {
                "value type ${value::class.simpleName} does not match spec " +
                    "${definition.spec::class.simpleName} for filter ${definition.spec.id}"
            }
            definition.predicate(item, value)
        }
    }
}

private fun valueMatches(spec: FilterSpec, value: FilterValue): Boolean =
    (spec is FilterSpec.Choice && value is FilterValue.Choice) ||
        (spec is FilterSpec.Range && value is FilterValue.Range) ||
        (spec is FilterSpec.Toggle && value is FilterValue.Toggle)

/**
 * Named sort over a generic item type, built from typed selectors so
 * comparisons stay compile-time checked. Chain with [then] for multi-key
 * sorts.
 */
class SortSpec<T> internal constructor(
    val id: String,
    internal val comparator: Comparator<T>,
) {
    /** Tiebreaker applied when the primary comparison is equal. */
    fun then(next: SortSpec<T>): SortSpec<T> =
        SortSpec("$id,${next.id}", comparator.thenComparing(next.comparator))

    companion object {
        fun <T, R : Comparable<R>> by(id: String, selector: (T) -> R): SortSpec<T> =
            SortSpec(id, compareBy(selector))

        /** Nulls place last unless [nullsLast] is false. */
        fun <T, R : Comparable<R>> byNullable(
            id: String,
            selector: (T) -> R?,
            nullsLast: Boolean = true,
        ): SortSpec<T> = SortSpec(id, nullableComparator(selector, nullsLast))
    }
}

private fun <T, R : Comparable<R>> nullableComparator(
    selector: (T) -> R?,
    nullsLast: Boolean,
): Comparator<T> = Comparator { a, b ->
    val left = selector(a)
    val right = selector(b)
    when {
        left == right -> 0
        left == null -> if (nullsLast) 1 else -1
        right == null -> if (nullsLast) -1 else 1
        else -> left.compareTo(right)
    }
}

/** Stable sort by [spec]; [descending] reverses the comparator, not stability. */
fun <T> sortItems(items: List<T>, spec: SortSpec<T>, descending: Boolean = false): List<T> =
    items.sortedWith(if (descending) spec.comparator.reversed() else spec.comparator)

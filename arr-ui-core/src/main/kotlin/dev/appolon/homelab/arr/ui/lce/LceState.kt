package dev.appolon.homelab.arr.ui.lce

import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult

/**
 * Loading / Content / Empty / Error state for one screen section. View models
 * expose this; [ArrLceBox] renders it. [Content.refreshing] lets a section
 * keep showing data while a pull-to-refresh runs instead of dropping back to
 * a spinner.
 */
sealed interface LceState<out T> {

    data object Loading : LceState<Nothing>

    data class Content<out T>(val data: T, val refreshing: Boolean = false) : LceState<T>

    data object Empty : LceState<Nothing>

    data class Error(val error: ArrError) : LceState<Nothing>

    companion object {
        /** Maps an [ArrResult] straight to an [LceState], with [isEmpty] detecting empty payloads. */
        fun <T> fromResult(result: ArrResult<T>, isEmpty: (T) -> Boolean = { false }): LceState<T> =
            when (result) {
                is ArrResult.Ok -> if (isEmpty(result.value)) Empty else Content(result.value)
                is ArrResult.Err -> Error(result.error)
            }
    }
}

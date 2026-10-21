package dev.appolon.homelab.arr.data.registry

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/** In-memory [PreferencesStore] for JVM tests. Open so tests can record writes. */
internal open class FakePreferencesStore : PreferencesStore {
    val strings = LinkedHashMap<String, String>()
    val ints = LinkedHashMap<String, Int>()

    private val changeTicks = MutableSharedFlow<Unit>(extraBufferCapacity = CHANGE_BUFFER)

    override suspend fun string(key: String): String? = strings[key]

    override suspend fun int(key: String): Int? = ints[key]

    override suspend fun putString(key: String, value: String) {
        strings[key] = value
        changeTicks.tryEmit(Unit)
    }

    override suspend fun putInt(key: String, value: Int) {
        ints[key] = value
        changeTicks.tryEmit(Unit)
    }

    override suspend fun remove(key: String) {
        strings.remove(key)
        ints.remove(key)
        changeTicks.tryEmit(Unit)
    }

    override suspend fun keys(): Set<String> = strings.keys + ints.keys

    override fun changes(): Flow<Unit> = changeTicks

    private companion object {
        const val CHANGE_BUFFER = 256
    }
}

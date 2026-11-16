package dev.appolon.homelab.arr.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel

/** Keeps screen state on the navigation entry until its connection changes. */
@Composable
internal fun ServerConnectionScope(version: Long, content: @Composable () -> Unit) {
    val holder: ConnectionViewModels = viewModel(key = "arr-connection-viewmodels") {
        ConnectionViewModels()
    }
    val owner = holder.ownerFor(version)
    key(version) {
        CompositionLocalProvider(LocalViewModelStoreOwner provides owner, content = content)
    }
}

internal class ConnectionViewModels : ViewModel() {
    private var version: Long? = null
    private var owner: ViewModelStoreOwner? = null

    fun ownerFor(version: Long): ViewModelStoreOwner {
        if (this.version != version) {
            owner?.viewModelStore?.clear()
            this.version = version
            owner = object : ViewModelStoreOwner {
                override val viewModelStore = ViewModelStore()
            }
        }
        return checkNotNull(owner)
    }

    override fun onCleared() {
        owner?.viewModelStore?.clear()
    }
}

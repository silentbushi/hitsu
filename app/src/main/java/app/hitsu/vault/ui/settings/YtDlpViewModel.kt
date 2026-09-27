package app.hitsu.vault.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hitsu.vault.data.download.CookieStore
import app.hitsu.vault.data.download.SavedCookies
import app.hitsu.vault.data.download.UpdateOutcome
import app.hitsu.vault.data.download.YtDlpEngine
import app.hitsu.vault.domain.AutoLock
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class YtDlpUiState(
    val version: String? = null,
    val lastCheckMillis: Long = 0L,
    val loading: Boolean = true,
    val updating: Boolean = false,
    val result: UpdateOutcome? = null,
    val cookies: List<SavedCookies> = emptyList(),
)

@HiltViewModel
class YtDlpViewModel @Inject constructor(
    private val engine: YtDlpEngine,
    private val cookies: CookieStore,
    private val autoLock: AutoLock,
) : ViewModel() {

    private val _state = MutableStateFlow(YtDlpUiState())
    val state: StateFlow<YtDlpUiState> = _state.asStateFlow()

    init {
        refreshCookies()
        viewModelScope.launch {
            _state.update {
                it.copy(version = engine.version(), lastCheckMillis = engine.lastCheckMillis, loading = false)
            }
        }
    }

    /** Spec §5.5: the login window is a hop the user asked for, so it must not close the vault. */
    fun onLoginLaunching() = autoLock.allowNextBackground()

    fun onDeleteCookies(domain: String) {
        viewModelScope.launch {
            cookies.delete(domain)
            refreshCookies()
        }
    }

    fun refreshCookies() {
        viewModelScope.launch {
            _state.update { it.copy(cookies = cookies.list()) }
        }
    }

    fun onUpdate() {
        if (_state.value.updating) return
        _state.update { it.copy(updating = true, result = null) }
        viewModelScope.launch {
            val outcome = engine.update()
            _state.update {
                it.copy(
                    updating = false,
                    result = outcome,
                    version = engine.version(),
                    lastCheckMillis = engine.lastCheckMillis,
                )
            }
        }
    }
}

package os.tessera.launcher

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import os.tessera.launcher.data.AppRepository
import os.tessera.launcher.data.LayoutStore
import os.tessera.launcher.data.LauncherApp
import os.tessera.launcher.layout.GridSpec

data class LauncherState(
    val apps: List<LauncherApp> = emptyList(),
    val homePins: List<String> = emptyList(),
    val dock: List<String> = emptyList(),
    val isLoading: Boolean = true,
)

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val grid = GridSpec.LARGE_PHONE
    private val repository = AppRepository(application.packageManager)
    private val store = LayoutStore(application)

    private val _state = MutableStateFlow(LauncherState())
    val state: StateFlow<LauncherState> = _state.asStateFlow()

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_PACKAGE_ADDED ||
                intent?.action == Intent.ACTION_PACKAGE_REMOVED
            ) refresh()
        }
    }

    init {
        refresh()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }
        application.registerReceiver(packageReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
    }

    override fun onCleared() {
        getApplication<Application>().unregisterReceiver(packageReceiver)
    }

    fun refresh() {
        val app = getApplication<Application>()
        viewModelScope.launch(Dispatchers.Default) {
            val apps = repository.load(app)
            store.seedFirstLayout(apps, grid.homeCapacity, grid.dockSlots)
            _state.value = LauncherState(
                apps = apps,
                homePins = store.homePins(),
                dock = store.dock(),
                isLoading = false,
            )
        }
    }

    fun launch(app: LauncherApp) {
        runCatching { getApplication<Application>().startActivity(app.launchIntent) }
            .onFailure { refresh() }
    }

    fun pin(app: LauncherApp) {
        store.pinToHome(app.id, grid.homeCapacity)
        _state.value = _state.value.copy(homePins = store.homePins())
    }

    fun unpin(app: LauncherApp) {
        store.unpinFromHome(app.id)
        _state.value = _state.value.copy(homePins = store.homePins())
    }

    fun pinToDock(app: LauncherApp) {
        store.pinToDock(app.id, grid.dockSlots)
        _state.value = _state.value.copy(homePins = store.homePins(), dock = store.dock())
    }

    fun removeFromDock(app: LauncherApp) {
        store.unpinFromDock(app.id)
        _state.value = _state.value.copy(homePins = store.homePins(), dock = store.dock())
    }

    fun openAppInfo(app: LauncherApp) {
        val intent = Intent(
            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", app.component.packageName, null),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { getApplication<Application>().startActivity(intent) }
    }

    fun byId(id: String): LauncherApp? = _state.value.apps.firstOrNull { it.id == id }
}

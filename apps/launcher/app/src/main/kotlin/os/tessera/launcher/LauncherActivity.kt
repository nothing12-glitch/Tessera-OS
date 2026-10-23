package os.tessera.launcher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import os.tessera.launcher.ui.AppActions
import os.tessera.launcher.ui.AppDrawer
import os.tessera.launcher.ui.HomeScreen
import os.tessera.launcher.ui.TesseraLauncherTheme

/**
 * Лаунчер як HOME-екран.
 *
 * Тема показує шпалери (`windowShowWallpaper`), тому палітра Monet, зібрана з
 * кольору шпалер, лягає прямо на них — той самий механізм, що й у стоці.
 * Стан шухляди тримаємо в активності, щоб повторне натискання кнопки
 * «додому» (`onNewIntent`) її закрило.
 */
class LauncherActivity : ComponentActivity() {

    private val viewModel: LauncherViewModel by viewModels()
    private var drawerOpen by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            TesseraLauncherTheme {
                LauncherRoot(
                    viewModel = viewModel,
                    drawerOpen = drawerOpen,
                    onOpenDrawer = { drawerOpen = true },
                    onCloseDrawer = { drawerOpen = false },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        drawerOpen = false
    }

    /** Застосунки встановлюються й видаляються без нашого відома — перечитуємо на поверненні. */
    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }
}

@Composable
private fun LauncherRoot(
    viewModel: LauncherViewModel,
    drawerOpen: Boolean,
    onOpenDrawer: () -> Unit,
    onCloseDrawer: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val actions = remember(viewModel) {
        AppActions(
            onLaunch = viewModel::launch,
            onPin = viewModel::pin,
            onUnpin = viewModel::unpin,
            onDock = viewModel::pinToDock,
            onUndock = viewModel::removeFromDock,
            onAppInfo = viewModel::openAppInfo,
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        HomeScreen(state = state, actions = actions, onOpenDrawer = onOpenDrawer)

        if (drawerOpen) {
            AppDrawer(
                apps = state.apps,
                homePins = state.homePins,
                actions = actions,
                onClose = onCloseDrawer,
            )
            BackHandler(onBack = onCloseDrawer)
        }

        if (state.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
    }
}

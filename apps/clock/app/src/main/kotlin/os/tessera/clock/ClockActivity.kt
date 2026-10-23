package os.tessera.clock

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import os.tessera.clock.ui.ClockScreen
import os.tessera.clock.ui.TesseraClockTheme

class ClockActivity : ComponentActivity() {

    private val viewModel: ClockViewModel by viewModels()

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            viewModel.setNotificationsGranted(granted)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // канал — до першого post, інакше на Android 8+ сповіщення не зʼявиться
        Notifications.ensureChannel(this)
        askNotificationsIfNeeded()

        setContent {
            TesseraClockTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                ClockScreen(state = state, viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // могли повернутись із налаштувань точних будильників чи дозволів
        viewModel.setNotificationsGranted(notificationsGranted())
        viewModel.refreshOnResume()
    }

    private fun notificationsGranted(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun askNotificationsIfNeeded() {
        if (!notificationsGranted()) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

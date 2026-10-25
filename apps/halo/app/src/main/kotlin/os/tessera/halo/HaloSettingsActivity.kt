package os.tessera.halo

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import os.tessera.halo.ui.HaloSettingsScreen
import os.tessera.halo.ui.HaloTheme

/**
 * Єдиний екран застосунку: налаштування краю. Сама підсвітка живе в
 * `HaloListenerService` і малюється поверх усього, тому тут лиш правила.
 */
class HaloSettingsActivity : ComponentActivity() {

    private val model: HaloViewModel by viewModels()

    private val askPostNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            model.refresh()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HaloTheme {
                val state by model.state.collectAsStateWithLifecycle()
                HaloSettingsScreen(
                    state = state,
                    onEnabled = model::onEnabled,
                    onPattern = model::onPattern,
                    onColor = model::onColor,
                    onDuration = model::onDuration,
                    onIntensity = model::onIntensity,
                    onOnlyLoud = model::onOnlyLoud,
                    onOpenOverlayGrant = model::onOpenOverlayGrant,
                    onOpenListenerSettings = model::onOpenListenerSettings,
                    onTest = model::onTest,
                    onAskPostPermission = {
                        askPostNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Дозвіл могли видати в Налаштуваннях, поки цей екран спав —
        // перечитуємо систему, а не власну пам'ять.
        model.refresh()
    }
}

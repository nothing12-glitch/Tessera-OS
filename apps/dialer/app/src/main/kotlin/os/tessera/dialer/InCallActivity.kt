package os.tessera.dialer

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import os.tessera.dialer.telecom.CallStateHolder
import os.tessera.dialer.ui.InCallScreen
import os.tessera.dialer.ui.TesseraDialerTheme

/**
 * Екран розмови.
 *
 * Показується поверх заблокованого екрана й вмикає дисплей: інакше на вхідний
 * дзвінок не було б чого відповісти, поки телефон лежить з погаслим екраном.
 *
 * Назад тут навмисно не виходить із розмови: власного сповіщення про дзвінок у
 * застосунку ще немає, тож закрити екран — значить втратити кнопки мікрофона
 * й завершення. Додому жест лишається робочим, дзвінок при цьому не чіпається.
 */
class InCallActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onCreate(savedInstanceState)

        setContent {
            TesseraDialerTheme {
                val state by CallStateHolder.state.collectAsStateWithLifecycle()
                // дзвінок закінчився — сервіс прибрав стан, і вікно не потрібне
                LaunchedEffect(state.visible) { if (!state.visible) finish() }
                BackHandler(enabled = state.visible) { }
                Scaffold(modifier = Modifier.fillMaxSize()) { insets ->
                    InCallScreen(
                        state = state,
                        onAnswer = CallStateHolder::answer,
                        onHangup = CallStateHolder::hangup,
                        onMute = CallStateHolder::toggleMute,
                        onHold = CallStateHolder::toggleHold,
                        onSpeaker = CallStateHolder::toggleSpeaker,
                        onToggleDialpad = { CallStateHolder.setDialpadVisible(!state.dialpadVisible) },
                        onPressKey = CallStateHolder::pressKey,
                        modifier = Modifier.padding(insets),
                    )
                }
            }
        }
    }
}

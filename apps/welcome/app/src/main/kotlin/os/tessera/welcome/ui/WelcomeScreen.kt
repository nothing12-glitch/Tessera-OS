package os.tessera.welcome.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import os.tessera.welcome.R
import os.tessera.welcome.WelcomeUiState
import os.tessera.welcome.logic.AccentColor
import os.tessera.welcome.logic.FontScaleChoice
import os.tessera.welcome.logic.LanguageChoice
import os.tessera.welcome.logic.MacPolicyChoice
import os.tessera.welcome.logic.NavigationChoice
import os.tessera.welcome.logic.PowerProfileChoice
import os.tessera.welcome.logic.RegionChoice
import os.tessera.welcome.logic.SystemScreen
import os.tessera.welcome.logic.ThemeChoice
import os.tessera.welcome.logic.WelcomeFlow
import os.tessera.welcome.logic.WelcomeStep

/**
 * Усі дії екрана одним об'єктом: кроків десять, і перелік lambda-параметрів
 * уже був би окремим файлом.
 */
data class WelcomeActions(
    val onNext: () -> Unit,
    val onBack: () -> Unit,
    val onSkip: () -> Unit,
    val onTheme: (ThemeChoice) -> Unit,
    val onAccent: (AccentColor) -> Unit,
    val onLanguage: (LanguageChoice) -> Unit,
    val onRegion: (RegionChoice) -> Unit,
    val onFontScale: (FontScaleChoice) -> Unit,
    val onHighContrast: (Boolean) -> Unit,
    val onNavigation: (NavigationChoice) -> Unit,
    val onPower: (PowerProfileChoice) -> Unit,
    val onMacPolicy: (MacPolicyChoice) -> Unit,
    val onOpenAccount: () -> Unit,
    val onOpenScreen: (SystemScreen) -> Unit,
)

/**
 * Екран першого запуску: повноекранний, десять кроків, один перехід між ними.
 *
 * Анімація переходу стокова і навмисно одна на всі кроки; власний рух є лише
 * там, де він і є змістом — у вітанні та в демо краю.
 */
@Composable
fun WelcomeScreen(
    state: WelcomeUiState,
    actions: WelcomeActions,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Header(state = state)

            Spacer(Modifier.height(24.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                AnimatedContent(
                    targetState = state.flow.step,
                    transitionSpec = {
                        (slideInHorizontally(tween(300)) { it / 5 } + fadeIn(tween(300)))
                            .togetherWith(
                                slideOutHorizontally(tween(200)) { -it / 5 } + fadeOut(tween(150)),
                            )
                    },
                    label = "welcome-step",
                ) { step ->
                    StepBody(step = step, state = state, actions = actions)
                }
            }

            Notice(notice = state.notice)

            Footer(state = state, actions = actions)

            BuildLine(state = state)
        }
    }
}

@Composable
private fun Header(state: WelcomeUiState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = stringResource(R.string.welcome_brand_desc),
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp)),
        )
        Spacer(Modifier.size(12.dp))
        Text(
            text = stringResource(R.string.welcome_brand),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.weight(1f))
        StepDots(state = state)
    }
}

/** Крапки замість «Крок N із M»: по одній на крок, пропущені — інші за кольором. */
@Composable
private fun StepDots(state: WelcomeUiState) {
    val scheme = MaterialTheme.colorScheme
    val active = WelcomeFlow.dotIndex(state.flow)
    val label = stringResource(R.string.welcome_dots_desc, active + 1, WelcomeFlow.dotCount())
    Row(
        modifier = Modifier
            .semantics(mergeDescendants = true) { contentDescription = label }
            .padding(start = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        WelcomeFlow.steps.forEachIndexed { index, step ->
            val color = when {
                index == active -> scheme.primary
                step in state.flow.skipped -> scheme.tertiary
                index < active -> scheme.primaryContainer
                else -> scheme.surfaceVariant
            }
            Box(
                modifier = Modifier
                    .size(if (index == active) 10.dp else 7.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

@Composable
private fun Notice(notice: Int?) {
    if (notice == null) return
    Text(
        text = stringResource(notice),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(vertical = 8.dp),
        textAlign = TextAlign.Start,
    )
}

/**
 * Низ: «Назад», «Пропустити» там, де пропуск справді є, і головна кнопка.
 *
 * Кожна кнопка має озвучуване ім'я окремим `contentDescription`: з дампів
 * `uiautomator` видно, що самого тексту для TalkBack і зовнішніх тестів не
 * дістаєш.
 */
@Composable
private fun Footer(state: WelcomeUiState, actions: WelcomeActions) {
    val flow = state.flow
    val atFinish = flow.step == WelcomeStep.FINISH
    val backDesc = stringResource(R.string.welcome_back_desc)
    val skipDesc = stringResource(R.string.welcome_skip_desc)
    val nextDesc = stringResource(
        if (atFinish) R.string.welcome_enter_desc else R.string.welcome_next_desc,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (WelcomeFlow.canGoBack(flow)) {
            TextButton(
                onClick = actions.onBack,
                modifier = Modifier.semantics { contentDescription = backDesc },
            ) {
                Text(stringResource(R.string.welcome_back))
            }
        }
        if (WelcomeFlow.canSkip(flow)) {
            TextButton(
                onClick = actions.onSkip,
                modifier = Modifier.semantics { contentDescription = skipDesc },
            ) {
                Text(stringResource(R.string.welcome_skip))
            }
        }
        Spacer(Modifier.weight(1f))
        Button(
            onClick = actions.onNext,
            modifier = Modifier.semantics { contentDescription = nextDesc },
        ) {
            Text(
                stringResource(
                    if (atFinish) R.string.welcome_done_button else R.string.welcome_next,
                ),
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }
}

/**
 * Дрібний рядок збірки знизу. На кроці вітання його не малюємо двічі: там
 * збірка — останній елемент анімації.
 */
@Composable
private fun BuildLine(state: WelcomeUiState) {
    if (state.buildLabel.isEmpty() || state.flow.step == WelcomeStep.GREETING) return
    Text(
        text = stringResource(R.string.welcome_build, state.buildLabel),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp),
    )
}

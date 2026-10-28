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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
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
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .widthIn(max = 600.dp)
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            ) {
                Header(state = state)
                Spacer(Modifier.height(32.dp))

                AnimatedContent(
                    targetState = state.flow.step,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    transitionSpec = {
                        (slideInHorizontally(tween(300)) { it / 5 } + fadeIn(tween(300)))
                            .togetherWith(
                                slideOutHorizontally(tween(200)) { -it / 5 } + fadeOut(tween(150)),
                            )
                    },
                    label = "welcome-step",
                ) { step ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 8.dp),
                    ) {
                        StepBody(step = step, state = state, actions = actions)
                    }
                }

                Notice(notice = state.notice)
                Footer(state = state, actions = actions)
                BuildLine(state = state)
            }
        }
    }
}

@Composable
private fun Header(state: WelcomeUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        StepProgress(state = state)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = stringResource(R.string.welcome_brand_desc),
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(4.dp),
            )
            Spacer(Modifier.size(12.dp))
            Text(
                text = stringResource(R.string.welcome_brand),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StepProgress(state: WelcomeUiState) {
    val scheme = MaterialTheme.colorScheme
    val active = WelcomeFlow.dotIndex(state.flow)
    val count = WelcomeFlow.dotCount()
    val label = stringResource(R.string.welcome_dots_desc, active + 1, count)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .semantics(mergeDescendants = true) {
                contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = (active + 1).toFloat(),
                    range = 0f..count.toFloat(),
                    steps = count - 1,
                )
            },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        WelcomeFlow.steps.forEachIndexed { index, step ->
            val color = when {
                index == active -> scheme.primary
                step in state.flow.skipped -> scheme.tertiary
                index < active -> scheme.primary.copy(alpha = 0.55f)
                else -> scheme.surfaceContainerHighest
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(3.dp)
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
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Footer(state: WelcomeUiState, actions: WelcomeActions) {
    val flow = state.flow
    val atFinish = flow.step == WelcomeStep.FINISH
    val backDesc = stringResource(R.string.welcome_back_desc)
    val skipDesc = stringResource(R.string.welcome_skip_desc)
    val nextDesc = stringResource(
        if (atFinish) R.string.welcome_enter_desc else R.string.welcome_next_desc,
    )
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (WelcomeFlow.canGoBack(flow)) {
                TextButton(
                    onClick = actions.onBack,
                    modifier = Modifier
                        .heightIn(min = 56.dp)
                        .semantics { contentDescription = backDesc },
                ) {
                    Text(stringResource(R.string.welcome_back))
                }
            }
            if (WelcomeFlow.canSkip(flow)) {
                TextButton(
                    onClick = actions.onSkip,
                    modifier = Modifier
                        .heightIn(min = 56.dp)
                        .semantics { contentDescription = skipDesc },
                ) {
                    Text(stringResource(R.string.welcome_skip))
                }
            }
        }
        Button(
            onClick = actions.onNext,
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier
                .then(if (atFinish || !WelcomeFlow.canGoBack(flow)) Modifier.fillMaxWidth() else Modifier)
                .heightIn(min = 56.dp)
                .semantics { contentDescription = nextDesc },
        ) {
            Text(
                stringResource(
                    if (atFinish) R.string.welcome_done_button else R.string.welcome_next,
                ),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                textAlign = TextAlign.Center,
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

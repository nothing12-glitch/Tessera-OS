package os.tessera.welcome.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import os.tessera.welcome.R
import os.tessera.welcome.WelcomeUiState
import os.tessera.welcome.logic.AccountRoute
import os.tessera.welcome.logic.ThemeChoice
import os.tessera.welcome.logic.WelcomeFlow
import os.tessera.welcome.logic.WelcomeStep

/**
 * Екран першого запуску: повноекранний, чотири кроки, один перехід між ними.
 *
 * Анімація тут стокова і навмисно одна на всі кроки: `AnimatedContent` із
 * зсувом по горизонталі. Власного руху, який хтось мусив би вимірювати на
 * плавність, немає — за рішенням користувача рух роблять засоби системи, а не
 * наш код.
 */
@Composable
fun WelcomeScreen(
    state: WelcomeUiState,
    onTheme: (ThemeChoice) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onSkipAccount: () -> Unit,
    onOpenAccount: () -> Unit,
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

            Spacer(Modifier.height(32.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.CenterStart,
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
                    when (step) {
                        WelcomeStep.GREETING -> Greeting(state = state)
                        WelcomeStep.THEME -> ThemeStep(state = state, onTheme = onTheme)

                        WelcomeStep.ACCOUNT -> AccountStep(state = state, onOpenAccount = onOpenAccount)
                        WelcomeStep.FINISH -> FinishStep(state = state)
                    }
                }
            }

            Notice(notice = state.notice)

            Footer(
                state = state,
                onBack = onBack,
                onNext = onNext,
                onSkipAccount = onSkipAccount,
            )
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
        Text(
            text = stringResource(
                R.string.welcome_step,
                WelcomeFlow.stepNumber(state.flow),
                WelcomeFlow.steps.size,
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(12.dp))
    LinearProgressIndicator(
        progress = { WelcomeFlow.progress(state.flow) },
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp)),
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
    )
}

@Composable
private fun StepTitle(title: String, body: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Greeting(state: WelcomeUiState) {
    Column {
        StepTitle(
            title = stringResource(R.string.welcome_greeting_title),
            body = stringResource(R.string.welcome_greeting_body),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.welcome_greeting_version, state.buildLabel),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ThemeStep(
    state: WelcomeUiState,
    onTheme: (ThemeChoice) -> Unit,
) {
    Column {
        StepTitle(
            title = stringResource(R.string.welcome_theme_title),
            body = stringResource(R.string.welcome_theme_body),
        )
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeChoice.entries.forEach { choice ->
                FilterChip(
                    selected = state.flow.theme == choice,
                    onClick = { onTheme(choice) },
                    label = {
                        Text(
                            text = when (choice) {
                                ThemeChoice.AUTO -> stringResource(R.string.welcome_theme_auto)
                                ThemeChoice.LIGHT -> stringResource(R.string.welcome_theme_light)
                                ThemeChoice.DARK -> stringResource(R.string.welcome_theme_dark)
                            },
                        )
                    },
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(
                if (state.themeApplied) R.string.welcome_theme_applied
                else R.string.welcome_theme_not_applied
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AccountStep(state: WelcomeUiState, onOpenAccount: () -> Unit) {
    val unavailable = state.account == AccountRoute.UNAVAILABLE
    Column {
        StepTitle(
            title = stringResource(R.string.welcome_account_title),
            body = stringResource(
                if (unavailable) R.string.welcome_account_none else R.string.welcome_account_body,
            ),
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onOpenAccount,
            enabled = !unavailable,
            colors = ButtonDefaults.buttonColors(),
        ) {
            Text(stringResource(R.string.welcome_account_open))
        }
    }
}

@Composable
private fun FinishStep(state: WelcomeUiState) {
    val themeName = when (state.flow.theme) {
        ThemeChoice.AUTO -> stringResource(R.string.welcome_theme_auto)
        ThemeChoice.LIGHT -> stringResource(R.string.welcome_theme_light)
        ThemeChoice.DARK -> stringResource(R.string.welcome_theme_dark)
    }
    Column {
        StepTitle(
            title = stringResource(R.string.welcome_finish_title),
            body = stringResource(R.string.welcome_finish_body, themeName),
        )
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

@Composable
private fun Footer(
    state: WelcomeUiState,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSkipAccount: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (WelcomeFlow.canGoBack(state.flow)) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.welcome_back)) }
        }
        if (state.flow.step == WelcomeStep.ACCOUNT) {
            TextButton(onClick = onSkipAccount) { Text(stringResource(R.string.welcome_account_skip)) }
        }
        Spacer(Modifier.weight(1f))
        Button(onClick = onNext) {
            Text(
                stringResource(
                    if (state.flow.step == WelcomeStep.FINISH) R.string.welcome_done_button
                    else R.string.welcome_next,
                ),
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }
}

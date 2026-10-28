package os.tessera.welcome.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import os.tessera.welcome.R
import os.tessera.welcome.WelcomeUiState
import os.tessera.welcome.logic.AccountRoute
import os.tessera.welcome.logic.AccentColor
import os.tessera.welcome.logic.EDGE_DEMO_CYCLE_MS
import os.tessera.welcome.logic.EDGE_DEMO_WAVES
import os.tessera.welcome.logic.FontScaleChoice
import os.tessera.welcome.logic.GREETING_MS
import os.tessera.welcome.logic.GreetingStage
import os.tessera.welcome.logic.LanguageChoice
import os.tessera.welcome.logic.MacPolicyChoice
import os.tessera.welcome.logic.NavigationChoice
import os.tessera.welcome.logic.PowerProfileChoice
import os.tessera.welcome.logic.RegionChoice
import os.tessera.welcome.logic.SystemScreen
import os.tessera.welcome.logic.ThemeChoice
import os.tessera.welcome.logic.WelcomeFlow
import os.tessera.welcome.logic.WelcomeStep
import os.tessera.welcome.logic.WriteState
import os.tessera.welcome.logic.WriteTarget
import os.tessera.welcome.logic.localeTag
import os.tessera.welcome.logic.stageOf
import os.tessera.welcome.logic.scaleOf

/** Один `when` на десять кроків: додати крок і забути його намалювати — не вийде. */
@Composable
internal fun StepBody(step: WelcomeStep, state: WelcomeUiState, actions: WelcomeActions) {
    when (step) {
        WelcomeStep.GREETING -> GreetingStep(state = state)
        WelcomeStep.LANGUAGE -> LanguageStep(state = state, actions = actions)
        WelcomeStep.ACCESSIBILITY -> AccessibilityStep(state = state, actions = actions)
        WelcomeStep.APPEARANCE -> AppearanceStep(state = state, actions = actions)
        WelcomeStep.NAVIGATION -> NavigationStep(state = state, actions = actions)
        WelcomeStep.POWER -> PowerStep(state = state, actions = actions)
        WelcomeStep.NETWORK -> NetworkStep(state = state, actions = actions)
        WelcomeStep.ACCOUNT -> AccountStep(state = state, actions = actions)
        WelcomeStep.EDGE_DEMO -> EdgeDemoStep()
        WelcomeStep.FINISH -> FinishStep(state = state)
    }
}

/** Варіант вибору разом з його підписом — щоб рядок чипів був одним кодом. */
private data class Option<T>(val value: T, val label: String)

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
private fun GroupLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 12.dp),
    )
}

/**
 * Рядок варіантів. Кожен тайл має озвучуване ім'я й стан: без нього й TalkBack,
 * і дамп `uiautomator` бачать порожній `focusable`-вузол.
 */
@Composable
private fun <T> ChoiceRow(
    options: List<Option<T>>,
    selected: T,
    descRes: Int,
    onSelect: (T) -> Unit,
) {
    val onState = stringResource(R.string.welcome_state_selected)
    val offState = stringResource(R.string.welcome_state_unselected)
    val scheme = MaterialTheme.colorScheme
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val columns = if (options.size != 3 && maxWidth / fontScale >= 300.dp) 2 else 1
        Column(
            modifier = Modifier.selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.chunked(columns).forEach { rowOptions ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowOptions.forEach { option ->
                        val isSelected = option.value == selected
                        val desc = stringResource(descRes, option.label)
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 56.dp)
                                .clip(MaterialTheme.shapes.extraLarge)
                                .semantics(mergeDescendants = true) {
                                    contentDescription = desc
                                    stateDescription = if (isSelected) onState else offState
                                }
                                .selectable(
                                    selected = isSelected,
                                    role = Role.RadioButton,
                                    onClick = { onSelect(option.value) },
                                ),
                            shape = MaterialTheme.shapes.extraLarge,
                            color = if (isSelected) {
                                scheme.primary.copy(alpha = 0.16f).compositeOver(scheme.surfaceContainer)
                            } else {
                                scheme.surfaceContainer
                            },
                            contentColor = scheme.onSurface,
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) scheme.primary.copy(alpha = 0.6f) else scheme.outlineVariant,
                            ),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = option.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.weight(1f),
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = scheme.primary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                    repeat(columns - rowOptions.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/**
 * Стан запису в систему — тричі чесно: ще не пробували / вийшло / кинуло
 * дозвіл. Жодного «застосовано» до спроби.
 */
@Composable
private fun WriteStateLine(state: WelcomeUiState, target: WriteTarget, appliedRes: Int, blockedRes: Int, pendingRes: Int) {
    val text = when (state.writeState(target)) {
        WriteState.NOT_ATTEMPTED -> stringResource(pendingRes)
        WriteState.APPLIED -> stringResource(appliedRes)
        WriteState.BLOCKED -> stringResource(blockedRes)
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp),
    )
}

/** Те, що модуль лише зберігає: рядок каже про це прямо, без удаваного успіху. */
@Composable
private fun StoredLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp),
    )
}

@Composable
private fun OpenScreenButton(label: String, description: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp)
            .heightIn(min = 56.dp)
            .semantics { contentDescription = description },
    ) {
        Text(label, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
    }
}

// 1. Вітання: три секунди появи знака, назви та тексту.

@Composable
private fun GreetingStep(state: WelcomeUiState) {
    val phase = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        phase.animateTo(1f, tween(durationMillis = GREETING_MS.toInt(), easing = LinearEasing))
    }
    val elapsed = phase.value * GREETING_MS
    val mark = stageOf(elapsed, GreetingStage.MARK)
    val title = stageOf(elapsed, GreetingStage.TITLE)
    val body = stageOf(elapsed, GreetingStage.BODY)
    val hint = stageOf(elapsed, GreetingStage.HINT)

    Column {
        Box(
            modifier = Modifier
                .size(104.dp)
                .alpha(mark)
                .scale(scaleOf(mark, 0.6f, 1f))
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = stringResource(R.string.welcome_greeting_mark_desc),
                modifier = Modifier.size(72.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.welcome_greeting_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .alpha(title)
                .scale(scaleOf(title, 0.92f, 1f)),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.welcome_greeting_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.alpha(body),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.welcome_greeting_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.alpha(hint),
        )
        // Останній елемент лишається невидимим до кінця анімації — інакше
        // «три секунди» скінчилися б на двох.
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.welcome_build, state.buildLabel),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.alpha(stageOf(elapsed, GreetingStage.BUILD)),
        )
    }
}

// 2. Мова й регіон: зберігаємо, системну мову відкриваємо.

@Composable
private fun LanguageStep(state: WelcomeUiState, actions: WelcomeActions) {
    Column {
        StepTitle(
            title = stringResource(R.string.welcome_language_title),
            body = stringResource(R.string.welcome_language_body),
        )
        Spacer(Modifier.height(24.dp))
        GroupLabel(stringResource(R.string.welcome_language_label))
        ChoiceRow(
            options = listOf(
                Option(LanguageChoice.UKRAINIAN, stringResource(R.string.welcome_lang_uk)),
                Option(LanguageChoice.ENGLISH, stringResource(R.string.welcome_lang_en)),
                Option(LanguageChoice.POLISH, stringResource(R.string.welcome_lang_pl)),
                Option(LanguageChoice.GERMAN, stringResource(R.string.welcome_lang_de)),
                Option(LanguageChoice.FRENCH, stringResource(R.string.welcome_lang_fr)),
                Option(LanguageChoice.SPANISH, stringResource(R.string.welcome_lang_es)),
            ),
            selected = state.flow.language,
            descRes = R.string.welcome_language_chip_desc,
            onSelect = actions.onLanguage,
        )
        Spacer(Modifier.height(12.dp))
        GroupLabel(stringResource(R.string.welcome_region_label))
        ChoiceRow(
            options = listOf(
                Option(RegionChoice.NONE, stringResource(R.string.welcome_region_none)),
                Option(RegionChoice.UKRAINE, stringResource(R.string.welcome_region_ua)),
                Option(RegionChoice.UNITED_STATES, stringResource(R.string.welcome_region_us)),
                Option(RegionChoice.UNITED_KINGDOM, stringResource(R.string.welcome_region_gb)),
                Option(RegionChoice.POLAND, stringResource(R.string.welcome_region_pl)),
                Option(RegionChoice.GERMANY, stringResource(R.string.welcome_region_de)),
                Option(RegionChoice.FRANCE, stringResource(R.string.welcome_region_fr)),
                Option(RegionChoice.SPAIN, stringResource(R.string.welcome_region_es)),
            ),
            selected = state.flow.region,
            descRes = R.string.welcome_region_chip_desc,
            onSelect = actions.onRegion,
        )
        StoredLine(
            stringResource(
                R.string.welcome_language_saved,
                localeTag(state.flow.language, state.flow.region),
            ),
        )
        OpenScreenButton(
            label = stringResource(R.string.welcome_language_open),
            description = stringResource(R.string.welcome_language_open_desc),
            onClick = { actions.onOpenScreen(SystemScreen.LOCALE) },
        )
    }
}

// 3. Доступність: масштаб шрифта пишемо в систему, контраст — лише зберігаємо.

@Composable
private fun AccessibilityStep(state: WelcomeUiState, actions: WelcomeActions) {
    val contrastDesc = stringResource(R.string.welcome_contrast_desc)
    Column {
        StepTitle(
            title = stringResource(R.string.welcome_access_title),
            body = stringResource(R.string.welcome_access_body),
        )
        Spacer(Modifier.height(24.dp))
        GroupLabel(stringResource(R.string.welcome_font_label))
        ChoiceRow(
            options = listOf(
                Option(FontScaleChoice.NORMAL, stringResource(R.string.welcome_font_normal)),
                Option(FontScaleChoice.LARGE, stringResource(R.string.welcome_font_large)),
                Option(FontScaleChoice.LARGEST, stringResource(R.string.welcome_font_largest)),
            ),
            selected = state.flow.fontScale,
            descRes = R.string.welcome_font_chip_desc,
            onSelect = actions.onFontScale,
        )
        WriteStateLine(
            state = state,
            target = WriteTarget.FONT_SCALE,
            appliedRes = R.string.welcome_font_applied,
            blockedRes = R.string.welcome_write_blocked,
            pendingRes = R.string.welcome_font_pending,
        )
        Spacer(Modifier.height(24.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.welcome_contrast),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.welcome_contrast_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = state.flow.highContrast,
                onCheckedChange = actions.onHighContrast,
                modifier = Modifier.semantics { contentDescription = contrastDesc },
            )
        }
        StoredLine(stringResource(R.string.welcome_talkback_hint))
        OpenScreenButton(
            label = stringResource(R.string.welcome_access_open),
            description = stringResource(R.string.welcome_access_open_desc),
            onClick = { actions.onOpenScreen(SystemScreen.ACCESSIBILITY) },
        )
    }
}

// 4. Вигляд: тема вручну пише система, акцент перемальовує це вікно.

@Composable
private fun AppearanceStep(state: WelcomeUiState, actions: WelcomeActions) {
    Column {
        StepTitle(
            title = stringResource(R.string.welcome_appearance_title),
            body = stringResource(R.string.welcome_appearance_body),
        )
        Spacer(Modifier.height(24.dp))
        GroupLabel(stringResource(R.string.welcome_theme_label))
        ChoiceRow(
            options = listOf(
                Option(ThemeChoice.LIGHT, stringResource(R.string.welcome_theme_light)),
                Option(ThemeChoice.DARK, stringResource(R.string.welcome_theme_dark)),
            ),
            selected = state.flow.theme,
            descRes = R.string.welcome_theme_chip_desc,
            onSelect = actions.onTheme,
        )
        WriteStateLine(
            state = state,
            target = WriteTarget.NIGHT_MODE,
            appliedRes = R.string.welcome_theme_applied,
            blockedRes = R.string.welcome_theme_not_applied,
            pendingRes = R.string.welcome_theme_pending,
        )
        Spacer(Modifier.height(24.dp))
        GroupLabel(stringResource(R.string.welcome_accent_label))
        AccentSwatches(
            selected = state.flow.accent,
            onSelect = actions.onAccent,
        )
        StoredLine(stringResource(R.string.welcome_accent_body))
    }
}

@Composable
private fun AccentSwatches(selected: AccentColor, onSelect: (AccentColor) -> Unit) {
    val labels = mapOf(
        AccentColor.DYNAMIC to stringResource(R.string.welcome_accent_dynamic),
        AccentColor.INDIGO to stringResource(R.string.welcome_accent_indigo),
        AccentColor.SKY to stringResource(R.string.welcome_accent_sky),
        AccentColor.VERDIGRIS to stringResource(R.string.welcome_accent_verdigris),
        AccentColor.AMBER to stringResource(R.string.welcome_accent_amber),
        AccentColor.EMBER to stringResource(R.string.welcome_accent_ember),
        AccentColor.NIGHT to stringResource(R.string.welcome_accent_night),
    )
    val onState = stringResource(R.string.welcome_state_selected)
    val offState = stringResource(R.string.welcome_state_unselected)
    AccentColor.entries.chunked(4).forEach { rowColors ->
        Row(
            modifier = Modifier.padding(bottom = 12.dp).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            rowColors.forEach { accent ->
                val label = labels.getValue(accent)
                val desc = stringResource(R.string.welcome_accent_chip_desc, label)
                val isSelected = accent == selected
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            // Monet не має одного кольору, і `colorScheme.primary`
                            // для його кружечка означав би, що після вибору
                            // будь-якого фіксованого акценту перший кружечок
                            // стає копією останнього. Градієнт із сидів цієї
                            // ж палітри каже те, що є: колір дає система.
                            brush = if (accent.isDynamic) {
                                Brush.linearGradient(
                                    listOf(
                                        Color(AccentColor.INDIGO.argb),
                                        Color(AccentColor.SKY.argb),
                                        Color(AccentColor.VERDIGRIS.argb),
                                        Color(AccentColor.AMBER.argb),
                                    ),
                                )
                            } else {
                                SolidColor(Color(accent.argb))
                            },
                        )
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                            shape = CircleShape,
                        )
                        .semantics(mergeDescendants = true) {
                            contentDescription = desc
                            stateDescription = if (isSelected) onState else offState
                        }
                        .selectable(
                            selected = isSelected,
                            role = Role.RadioButton,
                            onClick = { onSelect(accent) },
                        ),
                )
            }
        }
    }
}

// 5. Навігація.

@Composable
private fun NavigationStep(state: WelcomeUiState, actions: WelcomeActions) {
    Column {
        StepTitle(
            title = stringResource(R.string.welcome_nav_title),
            body = stringResource(R.string.welcome_nav_body),
        )
        Spacer(Modifier.height(24.dp))
        ChoiceRow(
            options = listOf(
                Option(NavigationChoice.GESTURES, stringResource(R.string.welcome_nav_gestures)),
                Option(NavigationChoice.BUTTONS, stringResource(R.string.welcome_nav_buttons)),
            ),
            selected = state.flow.navigation,
            descRes = R.string.welcome_nav_chip_desc,
            onSelect = actions.onNavigation,
        )
        StoredLine(
            stringResource(
                R.string.welcome_nav_saved,
                stringResource(
                    if (state.flow.navigation == NavigationChoice.GESTURES) {
                        R.string.welcome_nav_gestures
                    } else {
                        R.string.welcome_nav_buttons
                    },
                ),
            ),
        )
    }
}

// 6. Профіль живлення.

@Composable
private fun PowerStep(state: WelcomeUiState, actions: WelcomeActions) {
    Column {
        StepTitle(
            title = stringResource(R.string.welcome_power_title),
            body = stringResource(R.string.welcome_power_body),
        )
        Spacer(Modifier.height(24.dp))
        ChoiceRow(
            options = listOf(
                Option(PowerProfileChoice.ECONOMY, stringResource(R.string.welcome_power_economy)),
                Option(PowerProfileChoice.BALANCED, stringResource(R.string.welcome_power_balanced)),
                Option(PowerProfileChoice.PERFORMANCE, stringResource(R.string.welcome_power_performance)),
            ),
            selected = state.flow.power,
            descRes = R.string.welcome_power_chip_desc,
            onSelect = actions.onPower,
        )
        StoredLine(
            stringResource(
                R.string.welcome_power_saved,
                stringResource(powerLabelRes(state.flow.power)),
            ),
        )
        OpenScreenButton(
            label = stringResource(R.string.welcome_power_open),
            description = stringResource(R.string.welcome_power_open_desc),
            onClick = { actions.onOpenScreen(SystemScreen.SETTINGS) },
        )
    }
}

private fun powerLabelRes(choice: PowerProfileChoice): Int = when (choice) {
    PowerProfileChoice.ECONOMY -> R.string.welcome_power_economy
    PowerProfileChoice.BALANCED -> R.string.welcome_power_balanced
    PowerProfileChoice.PERFORMANCE -> R.string.welcome_power_performance
}

// 7. Приватність мережі.

@Composable
private fun NetworkStep(state: WelcomeUiState, actions: WelcomeActions) {
    val random = state.flow.macPolicy == MacPolicyChoice.RANDOM
    Column {
        StepTitle(
            title = stringResource(R.string.welcome_network_title),
            body = stringResource(R.string.welcome_network_body),
        )
        Spacer(Modifier.height(24.dp))
        ChoiceRow(
            options = listOf(
                Option(MacPolicyChoice.RANDOM, stringResource(R.string.welcome_mac_random)),
                Option(MacPolicyChoice.STABLE, stringResource(R.string.welcome_mac_stable)),
            ),
            selected = state.flow.macPolicy,
            descRes = R.string.welcome_mac_chip_desc,
            onSelect = actions.onMacPolicy,
        )
        Text(
            text = stringResource(
                if (random) R.string.welcome_mac_random_body else R.string.welcome_mac_stable_body,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        StoredLine(
            stringResource(
                R.string.welcome_network_saved,
                stringResource(
                    if (random) R.string.welcome_mac_random else R.string.welcome_mac_stable,
                ),
            ),
        )
        OpenScreenButton(
            label = stringResource(R.string.welcome_network_open),
            description = stringResource(R.string.welcome_network_open_desc),
            onClick = { actions.onOpenScreen(SystemScreen.NETWORK) },
        )
    }
}

// 8. Акаунт: чесна інструкція про microG, без імітації входу.

@Composable
private fun AccountStep(state: WelcomeUiState, actions: WelcomeActions) {
    val unavailable = state.account == AccountRoute.UNAVAILABLE
    val openDesc = stringResource(R.string.welcome_account_open_desc)
    Column {
        StepTitle(
            title = stringResource(R.string.welcome_account_title),
            body = stringResource(
                if (unavailable) R.string.welcome_account_none else R.string.welcome_account_body,
            ),
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = actions.onOpenAccount,
            enabled = !unavailable,
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .semantics { contentDescription = openDesc },
        ) {
            Text(stringResource(R.string.welcome_account_open))
        }
        if (WelcomeFlow.isSkipped(state.flow, WelcomeStep.ACCOUNT)) {
            StoredLine(stringResource(R.string.welcome_account_skipped))
        }
    }
}

// 9. Демо світлового краю: короткий рух, який і є зміст кроку.

@Composable
private fun EdgeDemoStep() {
    val edgeDesc = stringResource(R.string.welcome_edge_desc)
    Column {
        StepTitle(
            title = stringResource(R.string.welcome_edge_title),
            body = stringResource(R.string.welcome_edge_body),
        )
        Spacer(Modifier.height(24.dp))
        EdgeGlow(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .semantics(mergeDescendants = true) { contentDescription = edgeDesc },
        )
    }
}

/**
 * Смуга світла біжить по периметру — та сама математика, що й у краю `halo`,
 * але в межах екрана: анімація приклад, а не картинка.
 */
@Composable
private fun EdgeGlow(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "edge-demo")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = EDGE_DEMO_CYCLE_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "edge-phase",
    )
    val accent = MaterialTheme.colorScheme.primary
    val body = MaterialTheme.colorScheme.surfaceContainer
    Canvas(modifier = modifier) {
        val band = size.minDimension * 0.055f
        val inset = band * 2f
        val frame = Size(size.width - 2 * inset, size.height - 2 * inset)
        val radius = CornerRadius(band * 2.4f, band * 2.4f)
        val topLeft = Offset(inset, inset)
        val perimeter = 2f * (frame.width + frame.height)
        val segment = perimeter / (EDGE_DEMO_WAVES * 2f)

        drawRoundRect(
            color = body,
            topLeft = topLeft,
            size = frame,
            cornerRadius = radius,
        )
        drawRoundRect(
            color = accent.copy(alpha = 0.25f),
            topLeft = topLeft,
            size = frame,
            cornerRadius = radius,
            style = Stroke(width = band * 2.4f),
        )
        drawRoundRect(
            color = accent.copy(alpha = 0.95f),
            topLeft = topLeft,
            size = frame,
            cornerRadius = radius,
            style = Stroke(
                width = band * 0.9f,
                pathEffect = PathEffect.dashPathEffect(
                    floatArrayOf(segment * 0.45f, segment),
                    -phase * perimeter,
                ),
            ),
        )
    }
}

// 10. Готово: тур і три картки-підказки.

@Composable
private fun FinishStep(state: WelcomeUiState) {
    val themeLabel = stringResource(
        if (state.flow.theme == ThemeChoice.DARK) R.string.welcome_theme_dark
        else R.string.welcome_theme_light,
    )
    Column {
        StepTitle(
            title = stringResource(R.string.welcome_finish_title),
            body = stringResource(R.string.welcome_finish_body, themeLabel),
        )
        WriteStateLine(
            state = state,
            target = WriteTarget.PROVISION,
            appliedRes = R.string.welcome_provision_applied,
            blockedRes = R.string.welcome_provision_failed,
            pendingRes = R.string.welcome_provision_pending,
        )
        Spacer(Modifier.height(24.dp))
        GroupLabel(stringResource(R.string.welcome_tour_title))
        HintCard(
            title = stringResource(R.string.welcome_tip_halo_title),
            body = stringResource(R.string.welcome_tip_halo_body),
        )
        HintCard(
            title = stringResource(R.string.welcome_tip_wallpaper_title),
            body = stringResource(R.string.welcome_tip_wallpaper_body),
        )
        HintCard(
            title = stringResource(R.string.welcome_tip_settings_title),
            body = stringResource(R.string.welcome_tip_settings_body),
        )
    }
}

@Composable
private fun HintCard(title: String, body: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

package os.tessera.halo.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import os.tessera.halo.HaloUiState
import os.tessera.halo.R
import os.tessera.halo.logic.GlowColor
import os.tessera.halo.logic.GlowPattern
import os.tessera.halo.logic.HaloConfig
import java.util.Locale

@Composable
fun HaloSettingsScreen(
    state: HaloUiState,
    onEnabled: (Boolean) -> Unit,
    onPattern: (GlowPattern) -> Unit,
    onColor: (GlowColor) -> Unit,
    onDuration: (Long) -> Unit,
    onIntensity: (Float) -> Unit,
    onOnlyLoud: (Boolean) -> Unit,
    onOpenOverlayGrant: () -> Unit,
    onOpenListenerSettings: () -> Unit,
    onTest: () -> Unit,
    onAskPostPermission: () -> Unit,
) {
    val accent = Color(state.accentArgb)
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Header(accent)

            SectionTitle(stringResource(R.string.halo_section_permissions))
            GrantRow(
                title = stringResource(R.string.halo_grant_overlay_title),
                body = stringResource(R.string.halo_grant_overlay_body),
                granted = state.canDrawOverlays,
                action = stringResource(R.string.halo_grant_open),
                onAction = onOpenOverlayGrant,
            )
            GrantRow(
                title = stringResource(R.string.halo_grant_listener_title),
                body = stringResource(R.string.halo_grant_listener_body),
                granted = state.listenerGranted,
                action = stringResource(R.string.halo_grant_open),
                onAction = onOpenListenerSettings,
            )

            SectionTitle(stringResource(R.string.halo_section_look))
            SwitchRow(
                title = stringResource(R.string.halo_switch_title),
                body = stringResource(R.string.halo_switch_body),
                checked = state.config.enabled,
                onChange = onEnabled,
            )

            Text(
                stringResource(R.string.halo_pattern_title),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PatternChip(GlowPattern.WAVE, stringResource(R.string.halo_pattern_wave), state.config, onPattern)
                PatternChip(GlowPattern.PULSE, stringResource(R.string.halo_pattern_pulse), state.config, onPattern)
                PatternChip(GlowPattern.SOLID, stringResource(R.string.halo_pattern_solid), state.config, onPattern)
            }
            GlowPreview(
                pattern = state.config.pattern,
                color = accent,
                strength = state.config.intensity,
            )

            Text(
                stringResource(R.string.halo_color_title),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ColorChip(
                    label = stringResource(R.string.halo_color_monet),
                    selected = state.config.color == GlowColor.MONET,
                    onSelect = { onColor(GlowColor.MONET) },
                )
                ColorChip(
                    label = stringResource(R.string.halo_color_notification),
                    selected = state.config.color == GlowColor.NOTIFICATION,
                    onSelect = { onColor(GlowColor.NOTIFICATION) },
                )
            }
            Text(
                stringResource(
                    if (state.config.color == GlowColor.MONET) {
                        R.string.halo_color_monet_hint
                    } else {
                        R.string.halo_color_notification_hint
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            DurationSlider(state.config, onDuration)
            IntensitySlider(state.config, onIntensity)

            SectionTitle(stringResource(R.string.halo_section_when))
            SwitchRow(
                title = stringResource(R.string.halo_only_loud_title),
                body = stringResource(R.string.halo_only_loud_body),
                checked = state.config.onlyLoud,
                onChange = onOnlyLoud,
            )
            Text(
                stringResource(R.string.halo_zen_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionTitle(stringResource(R.string.halo_section_test))
            Button(
                onClick = onTest,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                Text(stringResource(R.string.halo_test_button))
            }
            if (!state.canPostNotifications) {
                OutlinedButton(
                    onClick = onAskPostPermission,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.halo_ask_post))
                }
            }
            state.notice?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            LimitsBlock()
        }
    }
}

@Composable
private fun Header(accent: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(top = 8.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(56.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(16.dp)),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(32.dp),
            )
        }
        Column {
            Text(
                stringResource(R.string.halo_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                stringResource(R.string.halo_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(Locale.getDefault()),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun GrantRow(title: String, body: String, granted: Boolean, action: String, onAction: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .size(10.dp)
                    .background(
                        if (granted) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        CircleShape,
                    ),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    if (granted) stringResource(R.string.halo_granted) else stringResource(R.string.halo_not_granted),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (granted) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
                Text(
                    body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!granted) {
                    TextButton(onClick = onAction) { Text(action) }
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}

@Composable
private fun PatternChip(
    pattern: GlowPattern,
    label: String,
    config: HaloConfig,
    onSelect: (GlowPattern) -> Unit,
) {
    FilterChip(
        selected = config.pattern == pattern,
        onClick = { onSelect(pattern) },
        label = { Text(label) },
    )
}

@Composable
private fun ColorChip(label: String, selected: Boolean, onSelect: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onSelect,
        label = { Text(label) },
    )
}

@Composable
private fun DurationSlider(config: HaloConfig, onChange: (Long) -> Unit) {
    val seconds = String.format(Locale.getDefault(), "%.1f", config.durationMs / 1000f)
    Column {
        Text(
            stringResource(R.string.halo_duration_title),
            style = MaterialTheme.typography.labelLarge,
        )
        Text(
            stringResource(R.string.halo_duration_value, seconds),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = config.durationMs.toFloat(),
            onValueChange = { onChange(it.toLong()) },
            valueRange = HaloConfig.MIN_DURATION_MS.toFloat()..HaloConfig.MAX_DURATION_MS.toFloat(),
        )
    }
}

@Composable
private fun IntensitySlider(config: HaloConfig, onChange: (Float) -> Unit) {
    val percent = (config.intensity * 100).toInt()
    Column {
        Text(
            stringResource(R.string.halo_intensity_title),
            style = MaterialTheme.typography.labelLarge,
        )
        Text(
            stringResource(R.string.halo_intensity_value, percent),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = config.intensity,
            onValueChange = onChange,
            valueRange = 0.2f..1f,
        )
    }
}

/**
 * Живий малюнок візерунка. Той самий математичний рух, що й у
 * `HaloGlowView`, але в межах екрана налаштувань.
 */
@Composable
private fun GlowPreview(pattern: GlowPattern, color: Color, strength: Float) {
    val transition = rememberInfiniteTransition(label = "halo-preview")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "halo-progress",
    )
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .padding(4.dp),
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(150.dp)) {
            val band = size.minDimension * 0.09f
            val inset = band
            val frameWidth = size.width - 2 * inset
            val frameHeight = size.height - 2 * inset
            val radius = CornerRadius(band * 1.5f, band * 1.5f)
            val perimeter = 2f * (frameWidth + frameHeight)
            val effect = if (pattern == GlowPattern.WAVE) {
                PathEffect.dashPathEffect(
                    floatArrayOf(perimeter * 0.17f, perimeter * 0.33f),
                    -progress * perimeter * 2f,
                )
            } else {
                null
            }
            val breathe = if (pattern == GlowPattern.PULSE) {
                0.35f + 0.65f * (0.5f - 0.5f * kotlin.math.cos(2.0 * Math.PI * 2f * progress).toFloat())
            } else {
                1f
            }
            val alpha = strength * breathe

            val halo = color.copy(alpha = alpha * 0.28f)
            val core = color.copy(alpha = alpha * 0.9f)

            drawRoundRect(
                color = halo,
                topLeft = Offset(inset, inset),
                size = Size(frameWidth, frameHeight),
                cornerRadius = radius,
                style = Stroke(width = band * 2f, pathEffect = effect),
            )
            drawRoundRect(
                color = core,
                topLeft = Offset(inset, inset),
                size = Size(frameWidth, frameHeight),
                cornerRadius = radius,
                style = Stroke(width = band * 0.55f, pathEffect = effect),
            )
        }
    }
}

@Composable
private fun LimitsBlock() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                stringResource(R.string.halo_limits_title),
                style = MaterialTheme.typography.titleSmall,
            )
            listOf(
                stringResource(R.string.halo_limit_screen_off),
                stringResource(R.string.halo_limit_not_led),
                stringResource(R.string.halo_limit_no_text),
                stringResource(R.string.halo_limit_listener),
                stringResource(R.string.halo_limit_no_service),
            ).forEach { line ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("•", color = MaterialTheme.colorScheme.primary)
                    Text(
                        line,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.halo_origin_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start,
            )
        }
    }
}

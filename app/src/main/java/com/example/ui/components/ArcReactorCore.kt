package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class CoreReactorMode {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING,
    ERROR
}

@Composable
fun ArcReactorCore(
    mode: CoreReactorMode,
    amplitude: Float,
    statusLabel: String,
    modifier: Modifier = Modifier,
    coreSize: Dp = 180.dp,
    showLabel: Boolean = true,
    onCoreClick: () -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val brightColor = MaterialTheme.colorScheme.onPrimaryContainer
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val errorColor = MaterialTheme.colorScheme.error
    val outlineColor = MaterialTheme.colorScheme.outline

    val activeAccent: Color = when (mode) {
        CoreReactorMode.IDLE -> primaryColor
        CoreReactorMode.LISTENING -> brightColor
        CoreReactorMode.THINKING -> tertiaryColor
        CoreReactorMode.SPEAKING -> primaryColor
        CoreReactorMode.ERROR -> errorColor
    }

    val infiniteTransition = rememberInfiniteTransition(label = "arc_reactor_transition")

    val slowRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (mode == CoreReactorMode.THINKING) 3600 else 14000,
                easing = LinearEasing
            )
        ),
        label = "slow_rotation"
    )

    val reverseRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (mode == CoreReactorMode.THINKING) 2400 else 9500,
                easing = LinearEasing
            )
        ),
        label = "reverse_rotation"
    )

    val pulsePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_phase"
    )

    val effectiveAmplitude by animateFloatAsState(
        targetValue = when (mode) {
            CoreReactorMode.LISTENING -> amplitude.coerceIn(0.15f, 1.0f)
            CoreReactorMode.SPEAKING -> 0.45f + pulsePhase * 0.45f
            CoreReactorMode.THINKING -> 0.35f + pulsePhase * 0.3f
            CoreReactorMode.IDLE -> 0.12f + pulsePhase * 0.1f
            CoreReactorMode.ERROR -> 0.25f
        },
        animationSpec = spring(stiffness = 380f),
        label = "reactive_amplitude"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(coreSize)
                .minimumInteractiveComponentSize()
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = true, color = activeAccent),
                    role = Role.Button,
                    onClickLabel = stringResource(R.string.cd_arc_reactor),
                    onClick = onCoreClick
                )
                .testTag("arc_reactor_button"),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val minDim = size.minDimension
                val center = Offset(size.width / 2f, size.height / 2f)
                val maxRadius = minDim * 0.47f

                // Outer radial holographic glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            activeAccent.copy(alpha = 0.22f + effectiveAmplitude * 0.16f),
                            activeAccent.copy(alpha = 0.06f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = minDim * 0.5f
                    ),
                    radius = minDim * 0.5f,
                    center = center
                )

                // Outer perimeter calibration ring
                drawCircle(
                    color = outlineColor.copy(alpha = 0.65f),
                    radius = maxRadius,
                    center = center,
                    style = Stroke(width = minDim * 0.008f)
                )

                // 36 calibrated HUD degree ticks + reactive audio spikes
                val tickCount = 36
                for (i in 0 until tickCount) {
                    val angleRad = (i * (360f / tickCount) + slowRotation * 0.25f) * (PI / 180.0)
                    val waveMod = if (mode == CoreReactorMode.LISTENING || mode == CoreReactorMode.SPEAKING) {
                        val harmonic = sin(i * 0.8 + pulsePhase * PI * 2).toFloat()
                        ((harmonic + 1f) * 0.5f) * effectiveAmplitude
                    } else if (i % 3 == 0) {
                        0.28f
                    } else {
                        0.12f
                    }
                    val innerR = maxRadius * 0.84f
                    val outerR = innerR + maxRadius * (0.05f + waveMod * 0.12f)
                    val start = Offset(
                        x = center.x + (cos(angleRad) * innerR).toFloat(),
                        y = center.y + (sin(angleRad) * innerR).toFloat()
                    )
                    val end = Offset(
                        x = center.x + (cos(angleRad) * outerR).toFloat(),
                        y = center.y + (sin(angleRad) * outerR).toFloat()
                    )
                    drawLine(
                        color = activeAccent.copy(alpha = if (i % 3 == 0) 0.9f else 0.45f),
                        start = start,
                        end = end,
                        strokeWidth = minDim * if (i % 3 == 0) 0.012f else 0.007f,
                        cap = StrokeCap.Round
                    )
                }

                // Segmented rotating arc ring
                rotate(degrees = slowRotation, pivot = center) {
                    val arcRadius = maxRadius * 0.72f
                    val topLeft = Offset(center.x - arcRadius, center.y - arcRadius)
                    val arcSize = Size(arcRadius * 2f, arcRadius * 2f)
                    val stroke = Stroke(width = minDim * 0.022f, cap = StrokeCap.Round)
                    listOf(0f, 120f, 240f).forEach { startDeg ->
                        drawArc(
                            color = activeAccent.copy(alpha = 0.85f),
                            startAngle = startDeg,
                            sweepAngle = 74f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = stroke
                        )
                    }
                }

                // Counter-rotating inner aperture ring
                rotate(degrees = reverseRotation, pivot = center) {
                    val innerArcRadius = maxRadius * 0.55f
                    val topLeft = Offset(center.x - innerArcRadius, center.y - innerArcRadius)
                    val arcSize = Size(innerArcRadius * 2f, innerArcRadius * 2f)
                    val stroke = Stroke(width = minDim * 0.015f, cap = StrokeCap.Butt)
                    listOf(15f, 105f, 195f, 285f).forEach { startDeg ->
                        drawArc(
                            color = brightColor.copy(alpha = 0.75f),
                            startAngle = startDeg,
                            sweepAngle = 52f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = stroke
                        )
                    }
                }

                // Inner core disc
                val coreRadius = maxRadius * (0.34f + effectiveAmplitude * 0.06f)
                drawCircle(
                    color = activeAccent.copy(alpha = 0.18f),
                    radius = coreRadius,
                    center = center
                )
                drawCircle(
                    color = activeAccent.copy(alpha = 0.92f),
                    radius = coreRadius,
                    center = center,
                    style = Stroke(width = minDim * 0.014f)
                )
            }

            Box(
                modifier = Modifier
                    .size(coreSize * 0.28f)
                    .clip(CircleShape)
                    .background(activeAccent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                val icon = when (mode) {
                    CoreReactorMode.IDLE -> Icons.Rounded.Mic
                    CoreReactorMode.LISTENING -> Icons.Rounded.GraphicEq
                    CoreReactorMode.THINKING -> Icons.Rounded.AutoAwesome
                    CoreReactorMode.SPEAKING -> Icons.AutoMirrored.Rounded.VolumeUp
                    CoreReactorMode.ERROR -> Icons.Rounded.WarningAmber
                }
                Icon(
                    imageVector = icon,
                    contentDescription = stringResource(R.string.cd_arc_reactor),
                    tint = activeAccent,
                    modifier = Modifier.size(coreSize * 0.16f)
                )
            }
        }

        if (showLabel) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = statusLabel,
                style = MaterialTheme.typography.labelLarge,
                color = activeAccent,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
    }
}

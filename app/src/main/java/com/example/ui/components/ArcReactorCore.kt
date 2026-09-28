package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ArcCyan
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonRed
import com.example.voice.SpeechState
import kotlin.math.cos
import kotlin.math.sin

/**
 * Responsive Arc Reactor Core.
 * Dynamically scales its concentric cyber rings and audio RMS glow to fit comfortably
 * on handheld mobile screens without crowding the transcript or system controls.
 */
@Composable
fun ArcReactorCore(
    speechState: SpeechState,
    audioRms: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    orbSize: androidx.compose.ui.unit.Dp = 120.dp,
    showStatePill: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "arc_reactor")

    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (speechState == SpeechState.LISTENING) 3000 else 10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val counterRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (speechState == SpeechState.LISTENING) 4000 else 14000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "counter_rotation"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val coreColor = when (speechState) {
        SpeechState.LISTENING -> ArcCyan
        SpeechState.PROCESSING -> NeonAmber
        SpeechState.SPEAKING -> NeonGreen
        SpeechState.ERROR -> NeonRed
        SpeechState.IDLE -> ArcCyan.copy(alpha = 0.9f)
    }

    val stateText = when (speechState) {
        SpeechState.LISTENING -> "LISTENING • SPEAK NOW"
        SpeechState.PROCESSING -> "PROCESSING QUERY..."
        SpeechState.SPEAKING -> "TRANSMITTING RESPONSE"
        SpeechState.ERROR -> "SYSTEM NOTICE // RETRY"
        SpeechState.IDLE -> "JARVIS READY • TAP TO SPEAK"
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(orbSize)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = androidx.compose.material3.ripple(bounded = true, color = coreColor),
                    onClick = onClick
                )
                .testTag("arc_reactor_orb")
        ) {
            val canvasSize = orbSize - 8.dp
            Canvas(modifier = Modifier.size(canvasSize)) {
                val center = Offset(size.width / 2, size.height / 2)
                val radius = size.width / 2 - 4.dp.toPx()

                // Outer ambient glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(coreColor.copy(alpha = 0.35f), Color.Transparent),
                        center = center,
                        radius = radius * 1.25f
                    )
                )

                // Outer static ring
                drawCircle(
                    color = coreColor.copy(alpha = 0.25f),
                    radius = radius,
                    style = Stroke(width = 1.5.dp.toPx())
                )

                // Dashed rotating ring
                val dashPathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 10f), 0f)
                drawCircle(
                    color = coreColor.copy(alpha = 0.7f),
                    radius = radius - 4.dp.toPx(),
                    style = Stroke(width = 2.dp.toPx(), pathEffect = dashPathEffect)
                )

                // 8 Arc Segment nodes (Iron Man Arc Reactor pattern)
                val nodeCount = 8
                val innerRadius = radius * 0.72f
                for (i in 0 until nodeCount) {
                    val angle = Math.toRadians((i * (360f / nodeCount) + rotationAngle).toDouble())
                    val x = center.x + (innerRadius * cos(angle)).toFloat()
                    val y = center.y + (innerRadius * sin(angle)).toFloat()

                    drawCircle(
                        color = coreColor,
                        radius = 2.5.dp.toPx(),
                        center = Offset(x, y)
                    )
                }

                // Inner counter-rotating segmented ring
                val innerDash = PathEffect.dashPathEffect(floatArrayOf(20f, 16f), counterRotation)
                drawCircle(
                    color = coreColor.copy(alpha = 0.9f),
                    radius = radius * 0.52f,
                    style = Stroke(width = 2.dp.toPx(), pathEffect = innerDash)
                )

                // Central pulsing core with audio RMS expansion
                val dynamicRms = if (speechState == SpeechState.LISTENING || speechState == SpeechState.SPEAKING) {
                    audioRms * 12.dp.toPx()
                } else 0f

                val coreRadius = (radius * 0.30f * pulseScale) + dynamicRms

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White, coreColor, coreColor.copy(alpha = 0.25f)),
                        center = center,
                        radius = coreRadius
                    ),
                    radius = coreRadius
                )

                // Center mechanical crosshair
                drawLine(
                    color = Color.White.copy(alpha = 0.85f),
                    start = Offset(center.x - 7.dp.toPx(), center.y),
                    end = Offset(center.x + 7.dp.toPx(), center.y),
                    strokeWidth = 1.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.85f),
                    start = Offset(center.x, center.y - 7.dp.toPx()),
                    end = Offset(center.x, center.y + 7.dp.toPx()),
                    strokeWidth = 1.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }

        if (showStatePill) {
            Spacer(modifier = Modifier.height(6.dp))

            // Prominent Tap-To-Engage Interactive Pill with 48dp minimum touch target
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(coreColor.copy(alpha = if (speechState == SpeechState.LISTENING) 0.22f else 0.10f))
                    .border(
                        1.dp,
                        coreColor.copy(alpha = if (speechState == SpeechState.LISTENING) 1f else 0.5f),
                        RoundedCornerShape(20.dp)
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = androidx.compose.material3.ripple(bounded = true, color = coreColor),
                        onClick = onClick
                    )
                    .padding(horizontal = 14.dp, vertical = 7.dp)
                    .testTag("tap_to_engage_button"),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Microphone State",
                        tint = coreColor,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stateText,
                        color = coreColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

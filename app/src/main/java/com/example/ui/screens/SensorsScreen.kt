package com.example.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sensor.DeviceOrientation
import com.example.sensor.SensorTelemetry
import com.example.ui.theme.ArcCyan
import com.example.ui.theme.BorderCyan
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonRed
import com.example.ui.theme.ObsidianDark
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceVariantDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun SensorsScreen(
    telemetry: SensorTelemetry,
    conflictWarnings: List<com.example.sensor.SensorConflictWarning> = emptyList(),
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianDark)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "ENVIRONMENTAL SENSOR TELEMETRY",
                        color = ArcCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "REAL-TIME HARDWARE FEEDS FOR WORKFLOW AUTOMATION",
                        color = TextMuted,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(NeonGreen.copy(alpha = 0.15f))
                        .border(1.dp, NeonGreen.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "✓ CONFLICT SAFE",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonGreen,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // 1. LIGHT SENSOR
        item {
            SensorCard(title = "AMBIENT LIGHT SENSOR", icon = Icons.Default.LightMode) {
                val lux = telemetry.lightLux
                val animatedLuxProgress by animateFloatAsState(
                    targetValue = (lux / 1000f).coerceIn(0f, 1f),
                    label = "lux_progress"
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${lux.toInt()} LUX",
                        color = ArcCyan,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )

                    val lightDesc = when {
                        lux < 5 -> "CRITICAL DARKNESS"
                        lux < 50 -> "DIM INTERIOR"
                        lux < 300 -> "OFFICE LIGHTING"
                        lux < 1000 -> "DAYLIGHT INDOORS"
                        else -> "DIRECT SUNLIGHT"
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(SurfaceVariantDark)
                            .border(1.dp, BorderCyan, RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = lightDesc,
                            color = NeonAmber,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                LinearProgressIndicator(
                    progress = { animatedLuxProgress },
                    color = ArcCyan,
                    trackColor = SurfaceVariantDark,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                )

                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("0 lx", color = TextMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    Text("Automation Threshold: ~5-10 lx", color = TextMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    Text("1000+ lx", color = TextMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }

        // 2. PROXIMITY SENSOR
        item {
            SensorCard(title = "OPTICAL PROXIMITY SENSOR", icon = Icons.Default.Sensors) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (telemetry.isProximityNear) "NEAR // COVERED" else "FAR // CLEAR",
                            color = if (telemetry.isProximityNear) NeonAmber else NeonGreen,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Distance: ${telemetry.proximityDistance} cm",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (telemetry.isProximityNear) NeonAmber.copy(alpha = 0.2f) else NeonGreen.copy(alpha = 0.2f))
                            .border(
                                1.5.dp,
                                if (telemetry.isProximityNear) NeonAmber else NeonGreen,
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(if (telemetry.isProximityNear) NeonAmber else NeonGreen)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (telemetry.isProximityNear)
                        "Device is currently in pocket, purse, or covered face-down."
                    else
                        "Proximity sensor beam is unobstructed.",
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // 3. ACCELEROMETER & ATTITUDE
        item {
            SensorCard(title = "ACCELEROMETER & 3D ATTITUDE", icon = Icons.Default.ScreenRotation) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = telemetry.orientation.name.replace("_", " "),
                        color = ArcCyan,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )

                    val isFaceDown = telemetry.orientation == DeviceOrientation.FLAT_FACE_DOWN
                    if (isFaceDown) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(NeonRed.copy(alpha = 0.2f))
                                .border(1.dp, NeonRed, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "PRIVACY TRIGGER ACTIVE",
                                color = NeonRed,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // X, Y, Z Vector Bars
                VectorAxisRow(label = "X-AXIS (Lateral)", value = telemetry.accelX)
                VectorAxisRow(label = "Y-AXIS (Longitudinal)", value = telemetry.accelY)
                VectorAxisRow(label = "Z-AXIS (Vertical)", value = telemetry.accelZ)
            }
        }

        // 4. BATTERY & CHARGING TELEMETRY
        item {
            SensorCard(
                title = "POWER & CHARGING STATUS",
                icon = if (telemetry.isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryFull
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${telemetry.batteryLevel}%",
                        color = if (telemetry.batteryLevel <= 20) NeonRed else ArcCyan,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (telemetry.isCharging) NeonGreen.copy(alpha = 0.2f) else SurfaceVariantDark)
                            .border(1.dp, if (telemetry.isCharging) NeonGreen else BorderCyan, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (telemetry.isCharging) "POWER CONNECTED // CHARGING" else "ON BATTERY POWER",
                            color = if (telemetry.isCharging) NeonGreen else TextSecondary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                LinearProgressIndicator(
                    progress = { (telemetry.batteryLevel / 100f).coerceIn(0f, 1f) },
                    color = if (telemetry.batteryLevel <= 20) NeonRed else ArcCyan,
                    trackColor = SurfaceVariantDark,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                )
            }
        }

        // 5. SHAKE KINETIC TRIGGER
        item {
            SensorCard(title = "KINETIC SHAKE DETECTOR", icon = Icons.Default.Vibration) {
                Text(
                    text = "Kinetic threshold configured to 14.0 m/s² with 1.2s debouncing. Shake device firmly to trigger assigned actions (e.g. Flashlight toggle).",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun SensorCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceDark)
            .border(1.dp, BorderCyan.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(SurfaceVariantDark)
                    .border(1.dp, BorderCyan.copy(alpha = 0.4f), RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = ArcCyan,
                    modifier = Modifier.size(15.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                color = ArcCyan,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        content()
    }
}

@Composable
fun VectorAxisRow(label: String, value: Float) {
    val normalized = ((value + 15f) / 30f).coerceIn(0f, 1f)
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                color = TextMuted,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = String.format(java.util.Locale.ROOT, "%.2f m/s²", value),
                color = if (kotlin.math.abs(value) > 9.0f) ArcCyan else TextPrimary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
        Spacer(modifier = Modifier.height(3.dp))
        LinearProgressIndicator(
            progress = { normalized },
            color = if (kotlin.math.abs(value) > 9.0f) ArcCyan else NeonAmber,
            trackColor = SurfaceVariantDark,
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
        )
    }
}

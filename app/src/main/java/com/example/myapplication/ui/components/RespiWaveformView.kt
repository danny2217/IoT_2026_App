package com.example.myapplication.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.model.RespirationPhase
import com.example.myapplication.ui.theme.ActiveGreen
import com.example.myapplication.ui.theme.MedicalBluePrimary
import com.example.myapplication.ui.theme.SoftCyanSecondary
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun RespiWaveformCard(
    respirationRate: Int,
    ieRatio: String,
    currentPhase: RespirationPhase,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val infiniteTransition = rememberInfiniteTransition(label = "pulseTransition")

    val waveTimeSeconds by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 3600f,
        animationSpec = infiniteRepeatable(
            animation = tween(3600000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "waveTimeSeconds"
    )

    val pingScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 2.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pingScale"
    )
    val pingAlpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pingAlpha"
    )

    val cyanColor = SoftCyanSecondary
    val blueColor = MedicalBluePrimary
    val grayColor = Color(0xFF737686)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("respi_waveform_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "호흡 주기 파형 그래프",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )

                val badgeBg = if (isDark) ActiveGreen.copy(alpha = 0.2f) else Color(0xFFECFDF5)
                val badgeBorder = if (isDark) ActiveGreen.copy(alpha = 0.45f) else Color(0xFFA7F3D0)
                val badgeText = if (isDark) ActiveGreen else Color(0xFF047857)

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(badgeBg)
                        .border(1.dp, badgeBorder, RoundedCornerShape(999.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(10.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .scale(pingScale)
                                .clip(CircleShape)
                                .background(ActiveGreen.copy(alpha = pingAlpha))
                        )
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(ActiveGreen)
                        )
                    }
                    Text(
                        text = "실시간 위상: ${currentPhase.displayName}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeText
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Graph Canvas Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val centerY = h * 0.52f
                    val amplitude = h * 0.35f

                    val pastEndX = w * 0.44f
                    val inspEndX = w * 0.68f
                    val activeEndX = w * 0.96f

                    val gridDash = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                    val gridLevels = listOf(0.2f, 0.4f, 0.6f, 0.8f)
                    for (level in gridLevels) {
                        drawLine(
                            color = grayColor.copy(alpha = 0.25f),
                            start = Offset(0f, h * level),
                            end = Offset(w, h * level),
                            strokeWidth = 1f,
                            pathEffect = gridDash
                        )
                    }

                    val vertDash = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    drawLine(
                        color = cyanColor.copy(alpha = 0.7f),
                        start = Offset(pastEndX, 0f),
                        end = Offset(pastEndX, h),
                        strokeWidth = 1.5f,
                        pathEffect = vertDash
                    )
                    drawLine(
                        color = blueColor,
                        start = Offset(inspEndX, 0f),
                        end = Offset(inspEndX, h),
                        strokeWidth = 2.5f,
                        pathEffect = vertDash
                    )

                    drawRect(
                        color = cyanColor.copy(alpha = 0.08f),
                        topLeft = Offset(pastEndX, 0f),
                        size = Size(inspEndX - pastEndX, h)
                    )
                    drawRect(
                        color = blueColor.copy(alpha = 0.08f),
                        topLeft = Offset(inspEndX, 0f),
                        size = Size(w - inspEndX, h)
                    )

                    fun calculateY(x: Float): Float {
                        val freq = (2f * PI.toFloat()) / (w * 0.48f)
                        val phaseShift = waveTimeSeconds * 2.2f
                        val rawWave = sin(x * freq - phaseShift)
                        return centerY - (rawWave * amplitude)
                    }

                    val pastPath = Path().apply {
                        moveTo(0f, calculateY(0f))
                        var x = 0f
                        while (x <= pastEndX) {
                            lineTo(x, calculateY(x))
                            x += 3f
                        }
                    }
                    drawPath(
                        path = pastPath,
                        color = grayColor.copy(alpha = 0.45f),
                        style = Stroke(
                            width = 2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                        )
                    )

                    val inspWavePath = Path().apply {
                        moveTo(pastEndX, calculateY(pastEndX))
                        var x = pastEndX
                        while (x <= inspEndX) {
                            lineTo(x, calculateY(x))
                            x += 3f
                        }
                    }
                    val inspFillPath = Path().apply {
                        addPath(inspWavePath)
                        lineTo(inspEndX, h)
                        lineTo(pastEndX, h)
                        close()
                    }
                    drawPath(
                        path = inspFillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(cyanColor.copy(alpha = 0.22f), Color.Transparent),
                            startY = 0f,
                            endY = h
                        )
                    )
                    drawPath(
                        path = inspWavePath,
                        color = cyanColor,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )

                    val activeWavePath = Path().apply {
                        moveTo(inspEndX, calculateY(inspEndX))
                        var x = inspEndX
                        while (x <= activeEndX) {
                            lineTo(x, calculateY(x))
                            x += 3f
                        }
                    }
                    val activeFillPath = Path().apply {
                        addPath(activeWavePath)
                        lineTo(activeEndX, h)
                        lineTo(inspEndX, h)
                        close()
                    }
                    drawPath(
                        path = activeFillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(blueColor.copy(alpha = 0.28f), Color.Transparent),
                            startY = 0f,
                            endY = h
                        )
                    )
                    drawPath(
                        path = activeWavePath,
                        color = blueColor,
                        style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                    )

                    val cursorY = calculateY(activeEndX)
                    drawCircle(
                        color = cyanColor.copy(alpha = pingAlpha),
                        radius = 10.dp.toPx() * pingScale,
                        center = Offset(activeEndX, cursorY),
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                    drawCircle(
                        color = blueColor,
                        radius = 5.dp.toPx(),
                        center = Offset(activeEndX, cursorY)
                    )
                }

                // 구간 배지 오버레이 (실시간 감지 중 삭제 처리)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Spacer(modifier = Modifier.fillMaxWidth(0.46f))
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color.White.copy(alpha = 0.92f))
                                .border(1.dp, cyanColor.copy(alpha = 0.5f), RoundedCornerShape(999.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(cyanColor)
                            )
                            Text(
                                text = "직전 흡기",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = cyanColor
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Spacer(modifier = Modifier.fillMaxWidth(0.70f))
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(blueColor.copy(alpha = 0.12f))
                                .border(1.dp, blueColor.copy(alpha = 0.3f), RoundedCornerShape(999.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(blueColor)
                            )
                            Text(
                                text = "호기 구간",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = blueColor
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Bottom Metrics ("시간당 호흡비(호기: 흡기)" 문구 적용)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "호흡수 (RR)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "$respirationRate",
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                lineHeight = 30.sp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "BPM",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "시간당 호흡비(호기: 흡기)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = ieRatio,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            lineHeight = 30.sp
                        )
                    }
                }
            }
        }
    }
}
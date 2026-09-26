package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.RespirationPhase
import com.example.ui.theme.ActiveGreen
import com.example.ui.theme.SoftCyanSecondary
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin

private data class PhaseInterval(
    val phase: RespirationPhase,
    val startX: Float,
    val endX: Float,
    val isLatest: Boolean
)

@Composable
fun RespiWaveformCard(
    respirationRate: Int,
    ieRatio: String,
    currentPhase: RespirationPhase,
    cycleProgress: Float,
    waveTimeSeconds: Float,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    val infiniteTransition = rememberInfiniteTransition(label = "pulseTransition")

    val pingScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 2.4f,
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

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("respi_waveform_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: Title + Phase Badge
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

                // Realtime phase indicator
                val badgeBg = if (isDark) ActiveGreen.copy(alpha = 0.2f) else Color(0xFFECFDF5)
                val badgeBorder = if (isDark) ActiveGreen.copy(alpha = 0.45f) else Color(0xFFA7F3D0)
                val badgeText = if (isDark) ActiveGreen else Color(0xFF047857)

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(badgeBg)
                        .border(1.dp, badgeBorder, RoundedCornerShape(999.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Row(
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
                            fontWeight = FontWeight.SemiBold,
                            color = badgeText
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Waveform Canvas Container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            ) {
                // Continuous Physics-Based Time Window:
                // Breath period T ~ 3.6s (60 ticks * 0.06s). Window length W = 5.4s (1.5 breath cycles).
                // Screen displays time interval [now - W, now] where x=w is 'now', x=0 is 'now - W'.
                val cyclePeriod = 3.6f
                val windowDuration = 5.4f
                val now = waveTimeSeconds

                // Mathematical breath wave function:
                fun getWaveY(t: Float, centerY: Float, amplitude: Float): Float {
                    val progressInCycle = ((t % cyclePeriod) + cyclePeriod) % cyclePeriod
                    val normalized = progressInCycle / cyclePeriod
                    // Realistic respiratory profile: smooth inhalation peak followed by relaxed exhalation
                    val angle = normalized * 2f * PI.toFloat()
                    val wave = sin(angle) - 0.22f * sin(2f * angle + 0.4f)
                    return centerY - (wave * amplitude * 0.78f)
                }

                fun getPhaseAt(t: Float): RespirationPhase {
                    val progressInCycle = ((t % cyclePeriod) + cyclePeriod) % cyclePeriod
                    return if (progressInCycle < (0.33f * cyclePeriod)) {
                        RespirationPhase.INSPIRATION
                    } else {
                        RespirationPhase.EXHALATION
                    }
                }

                val primaryColor = MaterialTheme.colorScheme.primary
                val secondaryColor = MaterialTheme.colorScheme.secondary
                val outlineColor = MaterialTheme.colorScheme.outline

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val centerY = h * 0.52f
                    val amplitude = h * 0.38f

                    fun timeToX(t: Float): Float {
                        return w * (1f - (now - t) / windowDuration)
                    }

                    fun xToTime(x: Float): Float {
                        return now - ((w - x) / w) * windowDuration
                    }

                    // 1. Horizontal dashed grid lines
                    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f)
                    val gridLevels = listOf(0.2f, 0.4f, 0.6f, 0.8f)
                    for (level in gridLevels) {
                        drawLine(
                            color = outlineColor.copy(alpha = 0.2f),
                            start = Offset(0f, h * level),
                            end = Offset(w, h * level),
                            strokeWidth = 1f,
                            pathEffect = dashEffect
                        )
                    }

                    // 2. Compute continuous phase transitions in the window [now - W, now]
                    // Cycles overlapping this window:
                    val startCycle = floor((now - windowDuration) / cyclePeriod).toInt() - 1
                    val endCycle = floor(now / cyclePeriod).toInt() + 1
                    val transitionTimes = mutableListOf<Float>()

                    for (k in startCycle..endCycle) {
                        val tInspToEnd = k * cyclePeriod + 0.33f * cyclePeriod
                        val tExpToEnd = (k + 1) * cyclePeriod
                        if (tInspToEnd in (now - windowDuration)..now) {
                            transitionTimes.add(tInspToEnd)
                        }
                        if (tExpToEnd in (now - windowDuration)..now) {
                            transitionTimes.add(tExpToEnd)
                        }
                    }
                    transitionTimes.sort()

                    // Build list of segments on screen from left (0) to right (w)
                    val boundariesX = mutableListOf<Float>()
                    boundariesX.add(0f)
                    for (t in transitionTimes) {
                        val x = timeToX(t)
                        if (x in 0f..w) {
                            boundariesX.add(x)
                        }
                    }
                    boundariesX.add(w)

                    val dividerDash = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)

                    // Draw each segment's background area and vertical divider
                    for (i in 0 until boundariesX.size - 1) {
                        val x1 = boundariesX[i]
                        val x2 = boundariesX[i + 1]
                        if (x2 <= x1) continue

                        val midT = xToTime((x1 + x2) * 0.5f)
                        val phase = getPhaseAt(midT)
                        val isLatestSegment = (i == boundariesX.size - 2)

                        // Highlight background
                        val areaColor = if (isLatestSegment) {
                            primaryColor.copy(alpha = if (isDark) 0.14f else 0.08f)
                        } else {
                            secondaryColor.copy(alpha = if (isDark) 0.10f else 0.06f)
                        }
                        drawRect(
                            color = areaColor,
                            topLeft = Offset(x1, 0f),
                            size = Size(x2 - x1, h)
                        )

                        // Divider line at the start of this segment (if inside screen)
                        if (x1 > 0f) {
                            val lineColor = if (isLatestSegment) primaryColor else secondaryColor.copy(alpha = 0.65f)
                            val lineWidth = if (isLatestSegment) 2.5f else 1.5f
                            drawLine(
                                color = lineColor,
                                start = Offset(x1, 0f),
                                end = Offset(x1, h),
                                strokeWidth = lineWidth,
                                pathEffect = dividerDash
                            )
                        }
                    }

                    // 3. Draw continuous flowing waveform across all segments
                    val wavePath = Path()
                    val stepPx = 3f
                    var curX = 0f
                    val firstY = getWaveY(xToTime(0f), centerY, amplitude)
                    wavePath.moveTo(0f, firstY)

                    while (curX <= w) {
                        val y = getWaveY(xToTime(curX), centerY, amplitude)
                        wavePath.lineTo(curX, y)
                        curX += stepPx
                    }

                    // Draw gradient under the latest/preceding waves
                    val fillPath = Path().apply {
                        addPath(wavePath)
                        lineTo(w, h)
                        lineTo(0f, h)
                        close()
                    }
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                primaryColor.copy(alpha = 0.25f),
                                secondaryColor.copy(alpha = 0.08f),
                                Color.Transparent
                            ),
                            startY = 0f,
                            endY = h
                        )
                    )

                    // Draw wave stroke
                    drawPath(
                        path = wavePath,
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                outlineColor.copy(alpha = 0.45f),
                                secondaryColor,
                                primaryColor
                            )
                        ),
                        style = Stroke(width = 3.8.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // 4. Cursor Indicator at current telemetry sample (right edge)
                    val cursorX = w * 0.97f
                    val cursorY = getWaveY(now, centerY, amplitude)
                    drawCircle(
                        color = secondaryColor.copy(alpha = pingAlpha),
                        radius = 12.dp.toPx() * pingScale,
                        center = Offset(cursorX, cursorY),
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                    drawCircle(
                        color = primaryColor.copy(alpha = 0.35f),
                        radius = 8.dp.toPx(),
                        center = Offset(cursorX, cursorY)
                    )
                    drawCircle(
                        color = primaryColor,
                        radius = 5.dp.toPx(),
                        center = Offset(cursorX, cursorY)
                    )
                }

                // Overlay Moving Badges:
                // Preceding phase sits in the previous segment; active phase sits in the latest segment!
                val activeIsInsp = currentPhase == RespirationPhase.INSPIRATION
                val activeLabel = if (activeIsInsp) "흡기 구간" else "호기 구간"
                val preLabel = if (activeIsInsp) "직전 호기" else "직전 흡기"

                // Compute exact moving x for dividers
                val startCycle = floor((now - windowDuration) / cyclePeriod).toInt() - 1
                val endCycle = floor(now / cyclePeriod).toInt() + 1
                val transitionTimes = mutableListOf<Float>()
                for (k in startCycle..endCycle) {
                    val t1 = k * cyclePeriod + 0.33f * cyclePeriod
                    val t2 = (k + 1) * cyclePeriod
                    if (t1 in (now - windowDuration)..now) transitionTimes.add(t1)
                    if (t2 in (now - windowDuration)..now) transitionTimes.add(t2)
                }
                transitionTimes.sort()

                val latestDividerFraction = if (transitionTimes.isNotEmpty()) {
                    (1f - (now - transitionTimes.last()) / windowDuration).coerceIn(0.15f, 0.95f)
                } else {
                    0.65f
                }
                val preDividerFraction = if (transitionTimes.size >= 2) {
                    (1f - (now - transitionTimes[transitionTimes.size - 2]) / windowDuration).coerceIn(0.05f, latestDividerFraction - 0.15f)
                } else {
                    (latestDividerFraction - 0.35f).coerceAtLeast(0.05f)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, start = 8.dp, end = 8.dp)
                ) {
                    Spacer(modifier = Modifier.weight(preDividerFraction.coerceAtLeast(0.05f)))

                    // Preceding Phase Badge (slides leftward as time advances)
                    val preBadgeBg = if (isDark) MaterialTheme.colorScheme.surface else Color.White.copy(alpha = 0.9f)
                    Box(
                        modifier = Modifier
                            .weight((latestDividerFraction - preDividerFraction).coerceIn(0.2f, 0.45f)),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(preBadgeBg)
                                .border(1.dp, secondaryColor.copy(alpha = 0.5f), RoundedCornerShape(999.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(secondaryColor)
                                )
                                Text(
                                    text = preLabel,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = secondaryColor
                                )
                            }
                        }
                    }

                    // Active Phase Badge (emerges at right and slides with the new boundary)
                    Column(
                        modifier = Modifier
                            .weight((1f - latestDividerFraction).coerceIn(0.25f, 0.45f)),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(primaryColor.copy(alpha = if (isDark) 0.22f else 0.12f))
                                .border(1.dp, primaryColor.copy(alpha = 0.4f), RoundedCornerShape(999.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(primaryColor)
                                )
                                Text(
                                    text = activeLabel,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = primaryColor
                                )
                            }
                        }
                        Text(
                            text = "실시간 감지 중",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium,
                            color = secondaryColor,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Bottom Metrics: 호흡수 (RR) & 호흡 시간비
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Metric 1: RR
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

                // Metric 2: 호흡 시간비
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
                            text = "호흡 시간비",
                            fontSize = 12.sp,
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

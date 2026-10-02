package com.example.myapplication.ui.components

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
import androidx.compose.ui.draw.alpha
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
import com.example.myapplication.viewmodel.PhaseSegment

@Composable
fun RespiWaveformView(
    respirationRate: Int,
    ieRatio: String,
    currentPhase: RespirationPhase,
    waveformBuffer: List<Float> = emptyList(),
    phaseSegments: List<PhaseSegment> = emptyList(),
    isConnected: Boolean = false,
    isDetectionMode: Boolean = true, // 감지 모드 여부 (일반 모드 시 false)
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val infiniteTransition = rememberInfiniteTransition(label = "pulseTransition")

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
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = if (isDetectionMode) 0.18f else 0.08f)),
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
                    color = if (isDetectionMode) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )

                val badgeBg     = if (!isDetectionMode) Color.LightGray.copy(alpha = 0.2f) else if (isDark) ActiveGreen.copy(alpha = 0.2f) else Color(0xFFECFDF5)
                val badgeBorder = if (!isDetectionMode) Color.Gray.copy(alpha = 0.3f) else if (isDark) ActiveGreen.copy(alpha = 0.45f) else Color(0xFFA7F3D0)
                val badgeText   = if (!isDetectionMode) Color.Gray else if (isDark) ActiveGreen else Color(0xFF047857)

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
                                .background(if (isDetectionMode) ActiveGreen.copy(alpha = if (isConnected) pingAlpha else 0f) else Color.Transparent)
                        )
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (isDetectionMode && isConnected) ActiveGreen else Color.Gray)
                        )
                    }
                    Text(
                        text = if (!isDetectionMode) "일반 모드 (비활성)" else if (isConnected) "실시간 감지 중" else "연결 대기 중",
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
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDetectionMode) 0.5f else 0.25f))
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        RoundedCornerShape(14.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize().alpha(if (isDetectionMode) 1f else 0.35f)) {
                    val w = size.width
                    val h = size.height

                    val gridDash = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                    for (level in listOf(0.2f, 0.4f, 0.6f, 0.8f)) {
                        drawLine(
                            color = grayColor.copy(alpha = 0.25f),
                            start = Offset(0f, h * level),
                            end   = Offset(w, h * level),
                            strokeWidth = 1f,
                            pathEffect = gridDash
                        )
                    }

                    val bufSize = waveformBuffer.size

                    // 일반 모드이거나 미연결/버퍼 부족 시 평탄선(Flatline) 표시
                    if (!isDetectionMode || !isConnected || bufSize < 2) {
                        val flatY = h * 0.5f
                        drawLine(
                            color = grayColor.copy(alpha = 0.35f),
                            start = Offset(0f, flatY),
                            end   = Offset(w, flatY),
                            strokeWidth = 2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                        )
                        return@Canvas
                    }

                    val minVal = waveformBuffer.min()
                    val maxVal = waveformBuffer.max()
                    val range  = (maxVal - minVal).let { if (it < 1f) 1f else it }
                    val margin = h * 0.12f

                    fun toPixelY(v: Float): Float {
                        val normalized = (v - minVal) / range
                        return (h - margin) - normalized * (h - 2 * margin)
                    }

                    fun toPixelX(idx: Int): Float = idx.toFloat() / (bufSize - 1).coerceAtLeast(1) * w

                    val vertDash = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    for (seg in phaseSegments) {
                        val segColor = when (seg.phase) {
                            RespirationPhase.INSPIRATION -> blueColor
                            RespirationPhase.EXHALATION  -> cyanColor
                        }
                        val startX = toPixelX(seg.startIndex.coerceIn(0, bufSize - 1))
                        val endX   = if (seg.endIndex == -1) w else toPixelX(seg.endIndex.coerceIn(0, bufSize - 1))

                        if (endX > startX) {
                            drawRect(
                                color    = segColor.copy(alpha = 0.08f),
                                topLeft  = Offset(startX, 0f),
                                size     = Size(endX - startX, h)
                            )
                            if (startX > 0f) {
                                drawLine(
                                    color       = segColor.copy(alpha = 0.6f),
                                    start       = Offset(startX, 0f),
                                    end         = Offset(startX, h),
                                    strokeWidth = 1.5f,
                                    pathEffect  = vertDash
                                )
                            }
                        }
                    }

                    if (phaseSegments.isEmpty()) {
                        val wavePath = Path().apply {
                            moveTo(toPixelX(0), toPixelY(waveformBuffer[0]))
                            for (i in 1 until bufSize) {
                                lineTo(toPixelX(i), toPixelY(waveformBuffer[i]))
                            }
                        }
                        drawPath(
                            path  = wavePath,
                            color = grayColor.copy(alpha = 0.6f),
                            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                        )
                    } else {
                        val firstSegStart = phaseSegments.firstOrNull()?.startIndex ?: 0
                        if (firstSegStart > 0) {
                            val pastPath = Path().apply {
                                moveTo(toPixelX(0), toPixelY(waveformBuffer[0]))
                                for (i in 1..firstSegStart.coerceAtMost(bufSize - 1)) {
                                    lineTo(toPixelX(i), toPixelY(waveformBuffer[i]))
                                }
                            }
                            drawPath(
                                path  = pastPath,
                                color = grayColor.copy(alpha = 0.4f),
                                style = Stroke(
                                    width      = 2.dp.toPx(),
                                    cap        = StrokeCap.Round,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                                )
                            )
                        }

                        for (seg in phaseSegments) {
                            val segColor = when (seg.phase) {
                                RespirationPhase.INSPIRATION -> blueColor
                                RespirationPhase.EXHALATION  -> cyanColor
                            }
                            val strokeW    = 2.5.dp.toPx()
                            val isCurrent  = seg.endIndex == -1
                            val startIdx   = seg.startIndex.coerceIn(0, bufSize - 1)
                            val endIdx     = if (seg.endIndex == -1) bufSize - 1 else seg.endIndex.coerceIn(0, bufSize - 1)

                            if (startIdx >= endIdx) continue

                            val segPath = Path().apply {
                                moveTo(toPixelX(startIdx), toPixelY(waveformBuffer[startIdx]))
                                for (i in (startIdx + 1)..endIdx) {
                                    lineTo(toPixelX(i), toPixelY(waveformBuffer[i]))
                                }
                            }

                            if (isCurrent) {
                                val fillPath = Path().apply {
                                    addPath(segPath)
                                    lineTo(toPixelX(endIdx), h)
                                    lineTo(toPixelX(startIdx), h)
                                    close()
                                }
                                drawPath(
                                    path  = fillPath,
                                    brush = Brush.verticalGradient(
                                        colors = listOf(segColor.copy(alpha = 0.22f), Color.Transparent),
                                        startY = 0f,
                                        endY   = h
                                    )
                                )
                            }
                            drawPath(
                                path  = segPath,
                                color = if (isCurrent) segColor else segColor.copy(alpha = 0.7f),
                                style = Stroke(width = strokeW, cap = StrokeCap.Round)
                            )
                        }
                    }

                    val cursorX = w
                    val cursorY = toPixelY(waveformBuffer.last())
                    val cursorColor = when (currentPhase) {
                        RespirationPhase.INSPIRATION -> blueColor
                        RespirationPhase.EXHALATION  -> cyanColor
                    }
                    drawCircle(
                        color  = cursorColor.copy(alpha = pingAlpha),
                        radius = 10.dp.toPx() * pingScale,
                        center = Offset(cursorX, cursorY),
                        style  = Stroke(width = 1.5.dp.toPx())
                    )
                    drawCircle(
                        color  = cursorColor,
                        radius = 5.dp.toPx(),
                        center = Offset(cursorX, cursorY)
                    )
                }

                if (!isDetectionMode) {
                    Text(
                        text = "일반 모드 작동 중 (호흡 감지 비활성화)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Legend
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 4.dp)
                    .alpha(if (isDetectionMode) 1f else 0.4f),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isDetectionMode) blueColor else Color.Gray)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "흡기 (Inspiration)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.width(20.dp))

                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isDetectionMode) cyanColor else Color.Gray)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "호기 (Exhalation)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Bottom Metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDetectionMode) 1f else 0.5f))
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text       = "호흡수 (RR)",
                            fontSize   = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color      = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            verticalAlignment    = Alignment.Bottom,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text       = if (isDetectionMode) "$respirationRate" else "--",
                                fontSize   = 26.sp,
                                fontWeight = FontWeight.Bold,
                                color      = if (isDetectionMode) MaterialTheme.colorScheme.primary else Color.Gray,
                                lineHeight = 30.sp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text       = "BPM",
                                fontSize   = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color      = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier   = Modifier.padding(bottom = 2.dp)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDetectionMode) 1f else 0.5f))
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text       = "시간당 호흡비(호기: 흡기)",
                            fontSize   = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color      = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text       = if (isDetectionMode) ieRatio else "--",
                            fontSize   = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color      = if (isDetectionMode) MaterialTheme.colorScheme.primary else Color.Gray,
                            lineHeight = 30.sp
                        )
                    }
                }
            }
        }
    }
}
package com.example.myapplication.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.ui.components.EmergencyStopButton
import com.example.myapplication.ui.theme.ActiveGreen
import com.example.myapplication.ui.theme.MedicalBlueContainer
import com.example.myapplication.ui.theme.SoftCyanContainer
import kotlin.math.sin

@Composable
fun TelemetryDashboardScreen() {
    val infiniteTransition = rememberInfiniteTransition(label = "Wave")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Phase"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // 1. Respiration Waveform
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Respiration Waveform",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        color = SoftCyanContainer.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.size(8.dp).background(SoftCyanContainer, shape = RoundedCornerShape(4.dp)))
                            Spacer(Modifier.width(6.dp))
                            Text("Expiration", fontSize = 12.sp, color = MedicalBlueContainer, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val midY = h / 2
                    drawLine(Color.LightGray.copy(0.3f), Offset(0f, midY), Offset(w, midY), strokeWidth = 2f)
                    val wavePath = Path()
                    wavePath.moveTo(0f, midY)
                    for (x in 0..w.toInt() step 4) {
                        val relX = x.toFloat() / w
                        val y = midY + sin(relX * 3 * Math.PI + phase).toFloat() * 40f
                        wavePath.lineTo(relX * w, y)
                    }
                    drawPath(wavePath, color = MedicalBlueContainer, style = Stroke(width = 5f))
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // 💡 2. AARC Posture Angle Gauge (2:1 정비율 반원 게이지로 찌그러짐 원천 해결)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "AARC Posture Angle",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(color = ActiveGreen.copy(alpha = 0.15f), shape = RoundedCornerShape(12.dp)) {
                        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ActiveGreen, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Optimal Position", fontSize = 11.sp, color = ActiveGreen, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 10.dp)) {
                    // 2:1 정비율 캔버스 지정
                    Canvas(modifier = Modifier.size(220.dp, 110.dp)) {
                        val stroke = 22f
                        val arcSize = Size(size.width - stroke, (size.height * 2) - stroke)
                        val topLeft = Offset(stroke / 2, stroke / 2)

                        // 트랙 배경 아크
                        drawArc(
                            color = Color(0xFFE0E3E5),
                            startAngle = 180f,
                            sweepAngle = 180f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = stroke)
                        )
                        // 타겟 각도 강조 아크 (30도)
                        drawArc(
                            color = ActiveGreen,
                            startAngle = 180f + 45f,
                            sweepAngle = 40f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = stroke)
                        )
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(top = 20.dp)
                    ) {
                        Text("30°", fontSize = 38.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                        Text("TILT ANGLE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    }
                }

                Spacer(Modifier.height(10.dp))
            }
        }

        Spacer(Modifier.height(20.dp))
        EmergencyStopButton(onClick = {})
    }
}
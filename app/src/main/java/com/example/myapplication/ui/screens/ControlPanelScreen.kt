package com.example.myapplication.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.ui.components.EmergencyStopButton
import com.example.myapplication.ui.theme.MedicalBlueContainer
import com.example.myapplication.ui.theme.VestGrayBackground

@Composable
fun ControlPanelScreen() {
    var intensityLevel by remember { mutableIntStateOf(3) } // 1 ~ 5 단계
    var hzFrequency by remember { mutableStateOf(12.5f) }
    val zoneActiveStates = remember { mutableStateListOf(true, true, false, false, true, true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Mode Filter Chips
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = true,
                onClick = {},
                label = { Text("Respiration Sync: ON", fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )
            FilterChip(
                selected = true,
                onClick = {},
                label = { Text("Posture Check: ON", fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )
        }

        Spacer(Modifier.height(12.dp))

        // 💡 1. Zone Control Card (가운데 정렬)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally // 완전 가운데 정렬
            ) {
                Text(
                    "Zone Control",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.align(Alignment.Start),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(Modifier.height(16.dp))

                // 조끼 실루엣 컨테이너
                Box(
                    modifier = Modifier
                        .size(190.dp, 220.dp)
                        .background(VestGrayBackground, shape = RoundedCornerShape(32.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier.fillMaxHeight(),
                        verticalArrangement = Arrangement.SpaceEvenly,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        for (row in 0..2) {
                            Row(
                                modifier = Modifier.width(135.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                for (col in 0..1) {
                                    val idx = row * 2 + col
                                    val isActive = zoneActiveStates[idx]
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .background(if (isActive) Color.White else Color(0xFFC0C4C8))
                                            .clickable { zoneActiveStates[idx] = !zoneActiveStates[idx] },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isActive) {
                                            Icon(
                                                Icons.Default.Vibration,
                                                contentDescription = null,
                                                tint = MedicalBlueContainer,
                                                modifier = Modifier.size(26.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text("${zoneActiveStates.count { it }} of 6 Zones Active", color = Color.Gray, fontSize = 13.sp)
            }
        }

        Spacer(Modifier.height(16.dp))

        // 💡 2. Intensity Dial (가운데 점 터치/슬라이드 조절기)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Intensity Level", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text("Level $intensityLevel", fontSize = 22.sp, fontWeight = FontWeight.Black, color = MedicalBlueContainer)
                }

                Spacer(Modifier.height(16.dp))

                // 1~5단계 스텝 점 셀렉터 UI
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, shape = CircleShape)
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (step in 1..5) {
                        val isSelected = step == intensityLevel
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(vertical = 4.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) MedicalBlueContainer else Color.Transparent)
                                .clickable { intensityLevel = step },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$step",
                                color = if (isSelected) Color.White else Color.Gray,
                                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Cycle Interval Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("CYCLE INTERVAL", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("$hzFrequency", fontSize = 28.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.width(4.dp))
                        Text("Hz", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = { if (hzFrequency > 1f) hzFrequency -= 0.5f },
                        modifier = Modifier.background(MedicalBlueContainer.copy(alpha = 0.15f), CircleShape)
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = null, tint = MedicalBlueContainer)
                    }
                    IconButton(
                        onClick = { hzFrequency += 0.5f },
                        modifier = Modifier.background(MedicalBlueContainer.copy(alpha = 0.15f), CircleShape)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = MedicalBlueContainer)
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        EmergencyStopButton(onClick = {})
    }
}
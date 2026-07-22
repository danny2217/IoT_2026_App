package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.myapplication.ui.components.RespiSyncBottomNav
import com.example.myapplication.ui.components.RespiSyncDrawerContent
import com.example.myapplication.ui.components.RespiSyncTopBar
import com.example.myapplication.ui.components.SettingsDialog
import com.example.myapplication.ui.screens.*
import com.example.myapplication.ui.theme.RespiSyncTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var isDarkMode by remember { mutableStateOf(false) }
            var isStarted by remember { mutableStateOf(false) }
            var selectedTab by remember { mutableIntStateOf(0) }
            var showSettingsDialog by remember { mutableStateOf(false) }

            val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
            val scope = rememberCoroutineScope()

            RespiSyncTheme(darkTheme = isDarkMode) {
                if (!isStarted) {
                    MainScreen(onStartClick = { isStarted = true })
                } else {
                    ModalNavigationDrawer(
                        drawerState = drawerState,
                        drawerContent = {
                            RespiSyncDrawerContent(
                                selectedTab = selectedTab,
                                onTabSelected = { tab ->
                                    selectedTab = tab
                                    scope.launch { drawerState.close() }
                                },
                                onOpenSettings = {
                                    scope.launch { drawerState.close() }
                                    showSettingsDialog = true
                                }
                            )
                        }
                    ) {
                        Scaffold(
                            topBar = {
                                RespiSyncTopBar(
                                    title = when (selectedTab) {
                                        0 -> "Device Pairing"
                                        1 -> "Telemetry Dashboard"
                                        2 -> "Control Panel"
                                        3 -> "Calibration"
                                        else -> "Device Pairing"
                                    },
                                    onMenuClick = { scope.launch { drawerState.open() } },
                                    onSettingsClick = { showSettingsDialog = true }
                                )
                            },
                            bottomBar = {
                                RespiSyncBottomNav(
                                    selectedItem = selectedTab,
                                    onItemSelected = { selectedTab = it }
                                )
                            }
                        ) { paddingValues ->
                            Box(modifier = Modifier.padding(paddingValues)) {
                                Crossfade(targetState = selectedTab, label = "TabCrossfade") { tab ->
                                    when (tab) {
                                        0 -> DevicePairingScreen()
                                        1 -> TelemetryDashboardScreen()
                                        2 -> ControlPanelScreen()
                                        3 -> CalibrationScreen()
                                    }
                                }
                            }
                        }
                    }

                    if (showSettingsDialog) {
                        SettingsDialog(
                            isDarkMode = isDarkMode,
                            onDarkModeChange = { isDarkMode = it },
                            onDismiss = { showSettingsDialog = false }
                        )
                    }
                }
            }
        }
    }
}
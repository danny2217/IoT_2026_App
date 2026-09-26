package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.NavTab
import com.example.ui.components.RespiBottomBar
import com.example.ui.components.RespiDrawerContent
import com.example.ui.components.RespiTopBar
import com.example.ui.components.SettingsDialog
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.MainScreen
import com.example.ui.screens.PairingScreen
import com.example.ui.theme.RespiSyncTheme
import com.example.viewmodel.RespiSyncViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: RespiSyncViewModel = viewModel()
            val uiState by viewModel.uiState.collectAsState()

            RespiSyncTheme(darkTheme = uiState.isDarkTheme) {
                RespiSyncApp(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RespiSyncApp(
    viewModel: RespiSyncViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    // If not started yet, display the user's initial 3D Lung MainScreen
    if (!uiState.isStarted) {
        MainScreen(onStartClick = { viewModel.startApp() })
        return
    }

    // Handle Back Button: if on Pairing tab, return to Dashboard; if on Dashboard, return to MainScreen
    BackHandler(enabled = drawerState.isOpen || uiState.currentTab != NavTab.DASHBOARD || uiState.isStarted) {
        if (drawerState.isOpen) {
            coroutineScope.launch { drawerState.close() }
        } else if (uiState.currentTab != NavTab.DASHBOARD) {
            viewModel.setTab(NavTab.DASHBOARD)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            RespiDrawerContent(
                state = uiState,
                onTabSelected = { tab ->
                    viewModel.setTab(tab)
                    coroutineScope.launch { drawerState.close() }
                },
                onCloseDrawer = {
                    coroutineScope.launch { drawerState.close() }
                },
                onToggleDarkTheme = { isDark ->
                    viewModel.toggleDarkTheme(isDark)
                }
            )
        }
    ) {
        Scaffold(
            topBar = {
                RespiTopBar(
                    deviceName = uiState.connectedDeviceName,
                    isConnected = uiState.isConnected,
                    onMenuClick = {
                        coroutineScope.launch { drawerState.open() }
                    },
                    onSettingsClick = {
                        viewModel.setSettingsDialogVisible(true)
                    }
                )
            },
            bottomBar = {
                RespiBottomBar(
                    currentTab = uiState.currentTab,
                    onTabSelected = { tab ->
                        viewModel.setTab(tab)
                    },
                    onOpenSettings = {
                        viewModel.setSettingsDialogVisible(true)
                    }
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                when (uiState.currentTab) {
                    NavTab.DASHBOARD -> {
                        DashboardScreen(
                            state = uiState,
                            onModeToggle = { viewModel.toggleDetectionMode() },
                            onPhaseChange = { phase -> viewModel.setPhase(phase) },
                            onIntensityChange = { level -> viewModel.setIntensity(level) }
                        )
                    }
                    NavTab.PAIRING -> {
                        PairingScreen(
                            state = uiState,
                            onStartScan = { viewModel.startScan() },
                            onConnectDevice = { id -> viewModel.connectDevice(id) },
                            onDisconnectDevice = { viewModel.disconnectDevice() },
                            onToggleAutoReconnect = { viewModel.toggleAutoReconnect(it) },
                            onRenameDevice = { newName -> viewModel.renameConnectedDevice(newName) }
                        )
                    }
                }
            }
        }
    }

    // Settings Dialog (Dark mode toggle & System Information)
    if (uiState.showSettingsDialog) {
        SettingsDialog(
            isDarkMode = uiState.isDarkTheme,
            connectedDeviceName = uiState.connectedDeviceName,
            deviceStatus = uiState.deviceStatus,
            onDarkModeChange = { viewModel.toggleDarkTheme(it) },
            onDismiss = { viewModel.setSettingsDialogVisible(false) }
        )
    }
}

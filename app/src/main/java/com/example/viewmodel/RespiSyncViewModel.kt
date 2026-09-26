package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.BleDeviceItem
import com.example.model.DetectionMode
import com.example.model.IntensityLevel
import com.example.model.NavTab
import com.example.model.RespirationPhase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sin

data class RespiUiState(
    val isStarted: Boolean = false,
    val isConnected: Boolean = true,
    val connectedDeviceName: String = "ESP32_Vest_BLE",
    val signalRssi: Int = -65,
    val batteryLevel: Int = 85,
    val isScanning: Boolean = false,
    val autoReconnect: Boolean = true,
    val deviceList: List<BleDeviceItem> = listOf(
        BleDeviceItem("esp32_01", "ESP32_Vest_BLE", -65, isConnected = true, isKnown = true),
        BleDeviceItem("ble_02", "Unknown_BLE_01", -88, isConnected = false, isKnown = false)
    ),
    val currentTab: NavTab = NavTab.DASHBOARD,
    val isDrawerOpen: Boolean = false,
    val isDarkTheme: Boolean = false,
    val showSettingsDialog: Boolean = false,
    
    // Respiratory telemetry
    val respirationRate: Int = 16,
    val ieRatio: String = "1 : 2.0",
    val currentPhase: RespirationPhase = RespirationPhase.EXHALATION,
    val detectionMode: DetectionMode = DetectionMode.DETECTION,
    val intensity: IntensityLevel = IntensityLevel.MEDIUM,
    val deviceStatus: String = "정상 작동 중",
    val isEmergencyStopped: Boolean = false,
    
    // Continuous cycle progress for dynamic wave streaming (0f to 1f)
    val cycleProgress: Float = 0.72f,
    val waveTimeSeconds: Float = 0f,
    val alertMessage: String? = null
)

class RespiSyncViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(RespiUiState())
    val uiState: StateFlow<RespiUiState> = _uiState.asStateFlow()

    init {
        startTelemetrySimulation()
    }

    private fun startTelemetrySimulation() {
        viewModelScope.launch {
            var tick = 0
            val totalTicksPerCycle = 60 // ~3.6s per respiratory breath cycle = ~16 BPM
            while (isActive) {
                delay(60) // smooth 16fps telemetry stream
                if (_uiState.value.isConnected && !_uiState.value.isEmergencyStopped) {
                    tick++
                    val pos = tick % totalTicksPerCycle
                    val progress = pos.toFloat() / totalTicksPerCycle

                    // Inspiration is roughly 33% of cycle, Exhalation is 67%
                    val phase = if (progress < 0.33f) {
                        RespirationPhase.INSPIRATION
                    } else {
                        RespirationPhase.EXHALATION
                    }

                    val dynamicRR = 16 + (sin(tick * 0.04) * 0.9).toInt()

                    _uiState.update { current ->
                        current.copy(
                            cycleProgress = progress,
                            waveTimeSeconds = tick * 0.06f,
                            currentPhase = phase,
                            respirationRate = dynamicRR.coerceIn(14, 18),
                            ieRatio = if (current.detectionMode == DetectionMode.DETECTION) "1 : 2.0" else "1 : 1.8"
                        )
                    }
                }
            }
        }
    }

    fun startApp() {
        _uiState.update { it.copy(isStarted = true) }
    }

    fun setTab(tab: NavTab) {
        _uiState.update { it.copy(currentTab = tab, isDrawerOpen = false) }
    }

    fun setDrawerOpen(isOpen: Boolean) {
        _uiState.update { it.copy(isDrawerOpen = isOpen) }
    }

    fun toggleDarkTheme(isDark: Boolean) {
        _uiState.update { it.copy(isDarkTheme = isDark) }
    }

    fun setSettingsDialogVisible(visible: Boolean) {
        _uiState.update { it.copy(showSettingsDialog = visible) }
    }

    fun toggleAutoReconnect(enabled: Boolean) {
        _uiState.update { it.copy(autoReconnect = enabled) }
    }

    fun setPhase(phase: RespirationPhase) {
        _uiState.update { it.copy(currentPhase = phase) }
    }

    fun setIntensity(level: IntensityLevel) {
        _uiState.update { it.copy(intensity = level) }
    }

    fun toggleDetectionMode() {
        val nextMode = if (_uiState.value.detectionMode == DetectionMode.DETECTION) {
            DetectionMode.GENERAL
        } else {
            DetectionMode.DETECTION
        }
        _uiState.update { it.copy(detectionMode = nextMode) }
    }

    fun connectDevice(deviceId: String) {
        viewModelScope.launch {
            _uiState.update { current ->
                val target = current.deviceList.find { it.id == deviceId }
                val updatedList = current.deviceList.map {
                    if (it.id == deviceId) it.copy(isConnected = true)
                    else it.copy(isConnected = false)
                }
                current.copy(
                    isConnected = true,
                    connectedDeviceName = target?.name ?: "ESP32_Vest_BLE",
                    deviceList = updatedList,
                    deviceStatus = "정상 작동 중",
                    isEmergencyStopped = false
                )
            }
        }
    }

    fun disconnectDevice() {
        _uiState.update { current ->
            current.copy(
                isConnected = false,
                deviceList = current.deviceList.map { it.copy(isConnected = false) },
                deviceStatus = "연결 끊김"
            )
        }
    }

    fun renameConnectedDevice(newName: String) {
        if (newName.isBlank()) return
        _uiState.update { current ->
            val updated = current.deviceList.map {
                if (it.isConnected) it.copy(name = newName) else it
            }
            current.copy(
                connectedDeviceName = newName,
                deviceList = updated
            )
        }
    }

    fun startScan() {
        if (_uiState.value.isScanning) return
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true) }
            delay(2000)
            _uiState.update { current ->
                current.copy(
                    isScanning = false,
                    deviceList = listOf(
                        BleDeviceItem("esp32_01", current.connectedDeviceName, -65, isConnected = current.isConnected, isKnown = true),
                        BleDeviceItem("ble_02", "Unknown_BLE_01", -88, isConnected = false, isKnown = false)
                    )
                )
            }
        }
    }

    fun triggerEmergencyStop() {
        _uiState.update {
            it.copy(
                isEmergencyStopped = true,
                deviceStatus = "비상 정지됨",
                alertMessage = "비상 정지가 작동되었습니다. 모든 가압 및 진동이 즉시 차단되었습니다."
            )
        }
    }

    fun resetEmergencyStop() {
        _uiState.update {
            it.copy(
                isEmergencyStopped = false,
                deviceStatus = "정상 작동 중",
                alertMessage = null
            )
        }
    }

    fun clearAlert() {
        _uiState.update { it.copy(alertMessage = null) }
    }
}

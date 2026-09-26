package com.example.myapplication.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.ble.BleConnectionState
import com.example.myapplication.ble.BleManager
import com.example.myapplication.model.BleDeviceItem
import com.example.myapplication.model.DetectionMode
import com.example.myapplication.model.IntensityLevel
import com.example.myapplication.model.NavTab
import com.example.myapplication.model.RespirationPhase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RespiUiState(
    val isStarted: Boolean = false,
    val isConnected: Boolean = false,
    val connectedDeviceName: String = "ESP32_Vest_BLE",
    val batteryLevel: Int = 100,
    val isScanning: Boolean = false,
    val autoReconnect: Boolean = true,
    val deviceList: List<BleDeviceItem> = emptyList(),
    val currentTab: NavTab = NavTab.PAIRING, // 첫 화면을 기기연결(PAIRING)로 설정
    val isDarkTheme: Boolean = false,
    val showSettingsDialog: Boolean = false,

    // Telemetry & 호흡 인식 결과
    val respirationRate: Int = 16,
    val ieRatio: String = "1 : 2.0",
    val currentPhase: RespirationPhase = RespirationPhase.EXHALATION,
    val detectionMode: DetectionMode = DetectionMode.DETECTION,
    val intensity: IntensityLevel = IntensityLevel.MEDIUM,
    val deviceStatus: String = "연결 대기 중",
    val chestPressure: Int = 0,
    val pressureHistory: List<Float> = emptyList()
)

class RespiSyncViewModel(application: Application) : AndroidViewModel(application) {
    private val bleManager = BleManager.getInstance(application)

    private val _uiState = MutableStateFlow(RespiUiState())
    val uiState: StateFlow<RespiUiState> = _uiState.asStateFlow()

    private var lastPhaseChangeTime = System.currentTimeMillis()
    private var inhaleDurationMs = 1200L
    private var exhaleDurationMs = 2400L
    private val pressureBuffer = mutableListOf<Float>()

    init {
        observeBleConnection()
        observeBleTelemetry()
        observeScannedDevices()
    }

    private fun observeBleConnection() {
        viewModelScope.launch {
            bleManager.connectionState.collect { connState ->
                val isConn = connState == BleConnectionState.CONNECTED
                _uiState.update { current ->
                    current.copy(
                        isConnected = isConn,
                        deviceStatus = when (connState) {
                            BleConnectionState.CONNECTED -> "정상 작동 중"
                            BleConnectionState.CONNECTING -> "연결 시도 중..."
                            BleConnectionState.RECONNECTING -> "재연결 중..."
                            BleConnectionState.DISCONNECTING -> "연결 해제 중..."
                            else -> "연결 해제됨"
                        }
                    )
                }
            }
        }
    }

    private fun observeBleTelemetry() {
        viewModelScope.launch {
            bleManager.telemetryData.collect { telemetry ->
                val now = System.currentTimeMillis()

                // 1. 호흡 상(Inhale/Exhale) 실시간 수신 및 자동 업데이트
                val newPhase = when (telemetry.respirationPhase) {
                    com.example.myapplication.ble.RespirationPhase.INHALE -> RespirationPhase.INSPIRATION
                    com.example.myapplication.ble.RespirationPhase.EXHALE -> RespirationPhase.EXHALATION
                    else -> _uiState.value.currentPhase
                }

                // 2. 호흡 상 변경 시 I:E 비율 및 RR 계산
                if (newPhase != _uiState.value.currentPhase) {
                    val duration = now - lastPhaseChangeTime
                    if (_uiState.value.currentPhase == RespirationPhase.INSPIRATION) {
                        inhaleDurationMs = duration.coerceAtLeast(500L)
                    } else {
                        exhaleDurationMs = duration.coerceAtLeast(500L)
                    }
                    lastPhaseChangeTime = now
                }

                val totalCycleMs = (inhaleDurationMs + exhaleDurationMs).toFloat()
                val calculatedRR = if (totalCycleMs > 0) (60000f / totalCycleMs).toInt().coerceIn(8, 30) else 16
                val ratioVal = if (inhaleDurationMs > 0) String.format("%.1f", exhaleDurationMs.toFloat() / inhaleDurationMs) else "2.0"

                pressureBuffer.add(telemetry.chestPressure.toFloat())
                if (pressureBuffer.size > 100) pressureBuffer.removeAt(0)

                _uiState.update { current ->
                    current.copy(
                        batteryLevel = telemetry.powerStatus,
                        currentPhase = newPhase,
                        respirationRate = calculatedRR,
                        ieRatio = "1 : $ratioVal",
                        chestPressure = telemetry.chestPressure,
                        pressureHistory = pressureBuffer.toList()
                    )
                }
            }
        }
    }

    private fun observeScannedDevices() {
        viewModelScope.launch {
            bleManager.scannedDevices.collect { devices ->
                _uiState.update { current ->
                    current.copy(
                        deviceList = devices.map {
                            BleDeviceItem(
                                id = it.address,
                                name = it.name,
                                rssi = it.rssi,
                                isConnected = it.address == bleManager.scannedDevices.value.find { d -> bleManager.isConnected() }?.address
                            )
                        }
                    )
                }
            }
        }
    }

    // --- 제어 및 상태 토글 ---
    fun startApp() { _uiState.update { it.copy(isStarted = true) } }
    fun setTab(tab: NavTab) { _uiState.update { it.copy(currentTab = tab) } }
    fun toggleDarkTheme(isDark: Boolean) { _uiState.update { it.copy(isDarkTheme = isDark) } }
    fun setSettingsDialogVisible(visible: Boolean) { _uiState.update { it.copy(showSettingsDialog = visible) } }

    fun startScan() { bleManager.startScan() }
    fun connectDevice(address: String) {
        val device = bleManager.scannedDevices.value.find { it.address == address }
        device?.let { bleManager.connectToDevice(it) }
    }
    fun disconnectDevice() { bleManager.disconnect() }

    // 자동 재연결 토글 동작 함수
    fun toggleAutoReconnect(enabled: Boolean) {
        _uiState.update { it.copy(autoReconnect = enabled) }
    }

    fun setIntensity(level: IntensityLevel) {
        _uiState.update { it.copy(intensity = level) }
        val levelInt = when (level) {
            IntensityLevel.LOW -> 1
            IntensityLevel.MEDIUM -> 3
            IntensityLevel.HIGH -> 5
        }
        bleManager.sendSetIntensity(levelInt)
    }

    fun toggleDetectionMode() {
        val nextMode = if (_uiState.value.detectionMode == DetectionMode.DETECTION) DetectionMode.GENERAL else DetectionMode.DETECTION
        _uiState.update { it.copy(detectionMode = nextMode) }
        bleManager.sendSetMode(if (nextMode == DetectionMode.DETECTION) 0x02.toByte() else 0x01.toByte())
    }
}
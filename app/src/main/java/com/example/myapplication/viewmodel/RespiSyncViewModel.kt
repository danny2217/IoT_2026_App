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

// ============================================================================
// [PhaseSegment] — 파형 버퍼 내 흡기/호기 구간 정보
// startIndex : waveformBuffer 내 이 구간이 시작하는 인덱스
// endIndex   : -1 이면 현재 진행 중(아직 끝나지 않음), 그 외는 끝 인덱스(포함)
// ============================================================================
data class PhaseSegment(
    val phase: RespirationPhase,
    val startIndex: Int,
    val endIndex: Int = -1  // -1 = 현재 진행 중
)

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
    val pressureHistory: List<Float> = emptyList(),

    // 실시간 파형 그래프용
    // waveformBuffer : ESP32의 filt 값(대역통과 필터링된 호흡 파형) 최대 300개 (약 15초 @ 20Hz)
    //                  최신 값이 리스트 끝(우측), 과거 값이 앞(좌측)
    val waveformBuffer: List<Float> = emptyList(),
    // phaseSegments  : waveformBuffer 내 각 흡기/호기 구간의 인덱스 범위
    //                  최대 10개 구간만 유지 (오래된 것 자동 삭제)
    val phaseSegments: List<PhaseSegment> = emptyList()
)

private const val WAVEFORM_BUFFER_SIZE = 300   // 15초 @ 20Hz
private const val MAX_PHASE_SEGMENTS   = 10    // 유지할 최대 구간 수

class RespiSyncViewModel(application: Application) : AndroidViewModel(application) {
    private val bleManager = BleManager.getInstance(application)

    private val _uiState = MutableStateFlow(RespiUiState())
    val uiState: StateFlow<RespiUiState> = _uiState.asStateFlow()

    private var lastPhaseChangeTime = System.currentTimeMillis()
    private var inhaleDurationMs = 1200L
    private var exhaleDurationMs = 2400L
    private val pressureBuffer = mutableListOf<Float>()

    // 파형 버퍼 및 구간 세그먼트 (내부 가변 상태)
    private val waveBuffer = mutableListOf<Float>()
    private val phaseSegs  = mutableListOf<PhaseSegment>()

    init {
        observeBleConnection()
        observeBleTelemetry()
        observeScannedDevices()
        observeScanningState()
    }

    private fun observeBleConnection() {
        viewModelScope.launch {
            bleManager.connectionState.collect { connState ->
                val isConn = connState == BleConnectionState.CONNECTED

                // 현재 연결된 BLE 기기의 MAC 주소 가져오기
                val connectedAddress = bleManager.scannedDevices.value
                    .find { bleManager.isConnected() }?.address

                _uiState.update { current ->
                    current.copy(
                        isConnected = isConn,

                        // ★ 핵심 수정: deviceList 내의 개별 기기 중 연결된 주소와 일치하는 항목만 isConnected = true로 변경
                        deviceList = current.deviceList.map { item ->
                            item.copy(isConnected = isConn && (item.id == connectedAddress))
                        },

                        deviceStatus = when (connState) {
                            BleConnectionState.CONNECTED -> "연결됨"
                            BleConnectionState.CONNECTING -> "연결 중..."
                            BleConnectionState.RECONNECTING -> "재연결 중..."
                            else -> "연결 안 됨"
                        }
                    )
                }
                // 연결 해제 시 파형 버퍼 초기화
                if (!isConn) {
                    waveBuffer.clear()
                    phaseSegs.clear()
                    _uiState.update { it.copy(waveformBuffer = emptyList(), phaseSegments = emptyList()) }
                }
            }
        }
    }

    private fun observeBleTelemetry() {
        viewModelScope.launch {
            bleManager.telemetryData.collect { telemetry ->
                val now = System.currentTimeMillis()

                // 1. 호흡 상 (Inhale/Exhale)
                val newPhase = when (telemetry.respirationPhase) {
                    com.example.myapplication.ble.RespirationPhase.INHALE -> RespirationPhase.INSPIRATION
                    com.example.myapplication.ble.RespirationPhase.EXHALE -> RespirationPhase.EXHALATION
                    else -> _uiState.value.currentPhase
                }

                // 2. 수신받은 duty(Byte 8) 값을 기반으로 현재 강도 매핑
                // (ESP32에서 150/200/250 또는 1/3/5 등으로 들어오는값 조건에 맞게 매핑)
                val mappedIntensity = when {
                    telemetry.duty <= 175 || telemetry.duty == 1 -> IntensityLevel.LOW
                    telemetry.duty in 175..225 || telemetry.duty == 3 -> IntensityLevel.MEDIUM
                    else -> IntensityLevel.HIGH
                }

                // 3. 수신받은 out(Byte 9) 값을 기반으로 현재 모드 매핑
                // (ESP32에서 0x02=감지모드, 0x01=일반모드로 보낼 경우)
                val mappedMode = if (telemetry.mode == 0x02 || telemetry.errorCode == 0x02) {
                    DetectionMode.DETECTION
                } else {
                    DetectionMode.GENERAL
                }

                // (이후 호흡수 및 I:E 비율 계산 로직 동일...)
                val prevPhase = _uiState.value.currentPhase
                if (newPhase != prevPhase) {
                    val duration = now - lastPhaseChangeTime
                    if (prevPhase == RespirationPhase.INSPIRATION) {
                        inhaleDurationMs = duration.coerceAtLeast(500L)
                    } else {
                        exhaleDurationMs = duration.coerceAtLeast(500L)
                    }
                    lastPhaseChangeTime = now

                    val currentBufSize = waveBuffer.size
                    if (phaseSegs.isNotEmpty() && phaseSegs.last().endIndex == -1) {
                        val lastSeg = phaseSegs.last()
                        phaseSegs[phaseSegs.size - 1] = lastSeg.copy(
                            endIndex = (currentBufSize - 1).coerceAtLeast(lastSeg.startIndex)
                        )
                    }
                    phaseSegs.add(PhaseSegment(phase = newPhase, startIndex = currentBufSize))
                }

                waveBuffer.add(telemetry.chestPressure.toFloat())
                val overflowCount = (waveBuffer.size - WAVEFORM_BUFFER_SIZE).coerceAtLeast(0)
                if (overflowCount > 0) {
                    waveBuffer.subList(0, overflowCount).clear()
                    val iter = phaseSegs.iterator()
                    val updated = mutableListOf<PhaseSegment>()
                    while (iter.hasNext()) {
                        val seg = iter.next()
                        val newStart = seg.startIndex - overflowCount
                        val newEnd   = if (seg.endIndex == -1) -1 else seg.endIndex - overflowCount
                        if (newEnd == -1 || newEnd >= 0) {
                            updated.add(seg.copy(startIndex = newStart.coerceAtLeast(0), endIndex = newEnd))
                        }
                    }
                    phaseSegs.clear()
                    phaseSegs.addAll(updated)
                }

                if (phaseSegs.size > MAX_PHASE_SEGMENTS) {
                    phaseSegs.subList(0, phaseSegs.size - MAX_PHASE_SEGMENTS).clear()
                }

                pressureBuffer.add(telemetry.chestPressure.toFloat())
                if (pressureBuffer.size > 100) pressureBuffer.removeAt(0)

                val totalCycleMs = (inhaleDurationMs + exhaleDurationMs).toFloat()
                val calculatedRR = if (totalCycleMs > 0) (60000f / totalCycleMs).toInt().coerceIn(8, 30) else 16
                val ratioVal = if (inhaleDurationMs > 0)
                    String.format("%.1f", exhaleDurationMs.toFloat() / inhaleDurationMs) else "2.0"

                // 4. UI State 업데이트 시 수신받은 강도와 모드 포함
                _uiState.update { current ->
                    current.copy(
                        batteryLevel     = telemetry.powerStatus,
                        currentPhase     = newPhase,
                        respirationRate  = calculatedRR,
                        ieRatio          = "1 : $ratioVal",
                        chestPressure    = telemetry.chestPressure,
                        pressureHistory  = pressureBuffer.toList(),
                        waveformBuffer   = waveBuffer.toList(),
                        phaseSegments    = phaseSegs.toList(),
                        intensity        = mappedIntensity, // <--- 추가: 수신받은 강도 반영
                        detectionMode    = mappedMode      // <--- 추가: 수신받은 모드 반영
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

    private fun observeScanningState() {
        viewModelScope.launch {
            bleManager.isScanning.collect { scanning ->
                _uiState.update { it.copy(isScanning = scanning) }
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
        bleManager.setAutoReconnectEnabled(enabled)
    }

    fun setIntensity(level: IntensityLevel) {
        _uiState.update { it.copy(intensity = level) }
        val levelInt = when (level) {
            IntensityLevel.LOW    -> 1
            IntensityLevel.MEDIUM -> 3
            IntensityLevel.HIGH   -> 5
        }
        bleManager.sendSetIntensity(levelInt)
    }

    fun toggleDetectionMode() {
        val nextMode = if (_uiState.value.detectionMode == DetectionMode.DETECTION) DetectionMode.GENERAL else DetectionMode.DETECTION
        _uiState.update { it.copy(detectionMode = nextMode) }
        bleManager.sendSetMode(if (nextMode == DetectionMode.DETECTION) 0x02.toByte() else 0x01.toByte())
    }
}
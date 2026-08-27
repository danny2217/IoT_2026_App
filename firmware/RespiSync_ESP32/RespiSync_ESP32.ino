/*
 * ============================================================================
 * [RespiSync] 스마트 객담 배출 조끼 - ESP32 Dual-Core 펌웨어
 * ============================================================================
 * 
 * 📋 역할 요약:
 *   - Core 0: 흉부 센서 ADC 읽기 → 이동평균 필터 → 호기/흡기 위상 판별
 *   - Core 1: BLE GATT Server 운영 + 모터 자체 주기 구동 + 텔레메트리 송신
 *
 * 🔌 하드웨어 연결:
 *   - GPIO 34: 흉부 압력 센서 (ADC, 아날로그 입력 전용 핀)
 *   - GPIO 18: 타격 모터 제어 (디지털 출력, HIGH = ON)
 *
 * 📡 BLE 프로토콜:
 *   - 앱 → ESP32: 고정 8바이트 명령 패킷 (Write)
 *   - ESP32 → 앱: 고정 12바이트 텔레메트리 패킷 (Notify, 20~50Hz)
 *
 * ⚠️ 보드를 받은 후 해야 할 일:
 *   1. Arduino IDE에서 "ESP32 Dev Module" 보드 선택
 *   2. 라이브러리 매니저에서 "NimBLE-Arduino" 설치 (h2zero 제작)
 *   3. 이 파일을 업로드
 *   4. 시리얼 모니터 115200 baud로 동작 확인
 *
 * 🔧 추후 수정이 필요한 부분은 모두 [TODO] 또는 [TUNE] 태그로 표시됨
 * ============================================================================
 */

#include <NimBLEDevice.h>  // NimBLE-Arduino 라이브러리 (경량 BLE 스택)

// ============================================================================
// [하드웨어 핀 설정] - 보드 회로에 맞게 이 숫자만 변경하면 됩니다
// ============================================================================
#define PIN_CHEST_SENSOR       34  // 흉부 압력 센서 ADC 핀 (GPIO 34 = ADC1_CH6)
#define PIN_MOTOR_CONTROL      18  // 타격 모터 제어 핀 (HIGH = 모터 ON)

// ============================================================================
// [BLE UUID 정의] - 안드로이드 앱과 반드시 동일해야 연결됩니다!
// ============================================================================
#define SERVICE_UUID           "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define CHARACTERISTIC_UUID_RX "beb5483e-36e1-4688-b7f5-ea07361b26a8"  // 앱 → ESP32 (Write)
#define CHARACTERISTIC_UUID_TX "ba000001-36e1-4688-b7f5-ea07361b26a8"  // ESP32 → 앱 (Notify)

// ============================================================================
// [명령 ID 정의] - 앱에서 보내는 Command 바이트
// 새 명령 추가 시: 여기에 #define 추가 → handleCommand() 안에 case 추가
// ============================================================================
#define CMD_START           0x01  // 타격 시작
#define CMD_STOP            0x02  // 타격 정지
#define CMD_EMERGENCY_STOP  0x03  // 긴급 정지 (즉시 모터 OFF + 상태 초기화)
#define CMD_CALIBRATE       0x04  // 캘리브레이션 모드 진입
#define CMD_SET_PERIOD      0x05  // 타격 주기 변경 (모터 동작 중에도 적용)
// [TODO] 새 명령 추가 예시:
// #define CMD_SET_INTENSITY  0x06  // 강도 조절 (PWM 제어 시)
// #define CMD_ZONE_SELECT    0x07  // 특정 존 활성화/비활성화

// ============================================================================
// [장치 상태 코드] - 텔레메트리 패킷의 Device State 바이트
// ============================================================================
#define STATE_IDLE         0x00
#define STATE_RUNNING      0x01
#define STATE_CALIBRATING  0x02
#define STATE_ERROR        0xFF

// ============================================================================
// [호흡 위상 코드] - Core 0에서 판별한 호흡 상태
// ============================================================================
#define PHASE_NONE     0x00  // 판별 불가 (초기 상태 또는 무호흡)
#define PHASE_INHALE   0x01  // 흡기 (들숨) - 압력 증가
#define PHASE_EXHALE   0x02  // 호기 (날숨) - 압력 감소, 타격 적합 시점

// ============================================================================
// [이동평균 필터 설정]
// ============================================================================
#define FILTER_WINDOW_SIZE  10  // [TUNE] 이동평균 윈도우 크기 (10 = 100ms 구간 평균)
                                // 값이 크면 안정적이지만 반응 느림
                                // 값이 작으면 민감하지만 노이즈에 취약

// ============================================================================
// [텔레메트리 송신 주기]
// ============================================================================
#define TELEMETRY_INTERVAL_MS  50  // [TUNE] 50ms = 20Hz (앱 요구사항: 20~50Hz)
                                    // 20ms로 변경하면 50Hz

// ============================================================================
// 전역 변수 (Core 간 공유 - volatile 또는 Mutex 보호)
// ============================================================================

// --- Core 0 → Core 1 공유 데이터 (호흡 인지 결과) ---
volatile uint8_t  g_respirationPhase = PHASE_NONE;  // 현재 호흡 위상
volatile int16_t  g_filteredPressure = 0;           // 필터링된 센서값

// --- Core 1 내부 상태 ---
volatile bool     g_motorRunning = false;      // 모터 주기 구동 활성 여부
volatile bool     g_motorPulseActive = false;   // 현재 순간 모터 ON 여부
volatile uint16_t g_strikePeriodMs = 500;       // 타격 주기 (ms), 기본 500ms
volatile uint8_t  g_deviceState = STATE_IDLE;   // 현재 장치 상태

// --- BLE 연결 상태 ---
volatile bool g_bleConnected = false;

// --- BLE 객체 포인터 ---
NimBLECharacteristic* pTxCharacteristic = nullptr;  // Notify용 TX 캐릭터리스틱

// ============================================================================
// [이동평균 필터 버퍼] - Core 0에서 사용
// ============================================================================
int16_t filterBuffer[FILTER_WINDOW_SIZE] = {0};
int     filterIndex = 0;
int32_t filterSum = 0;

// ============================================================================
// [BLE 콜백 클래스] - 연결/해제 이벤트 처리
// ============================================================================
class ServerCallbacks : public NimBLEServerCallbacks {
    void onConnect(NimBLEServer* pServer) override {
        g_bleConnected = true;
        Serial.println("[BLE] 앱 연결됨!");
    }

    void onDisconnect(NimBLEServer* pServer) override {
        g_bleConnected = false;
        
        // ★ [Fail-Safe] BLE 연결 끊기면 즉시 모터 정지 (환자 안전)
        g_motorRunning = false;
        g_motorPulseActive = false;
        digitalWrite(PIN_MOTOR_CONTROL, LOW);
        g_deviceState = STATE_IDLE;
        
        Serial.println("[BLE] 연결 끊김 → 모터 긴급 정지!");
        
        // 재광고 시작 (앱이 다시 연결할 수 있도록)
        NimBLEDevice::startAdvertising();
        Serial.println("[BLE] 재광고 시작...");
    }
};

// ============================================================================
// [명령 수신 콜백] - 앱에서 8바이트 패킷을 Write하면 호출됨
// ============================================================================
class RxCallbacks : public NimBLECharacteristicCallbacks {
    void onWrite(NimBLECharacteristic* pCharacteristic) override {
        std::string rxData = pCharacteristic->getValue();
        
        // 패킷 길이 검증 (고정 8바이트)
        if (rxData.length() != 8) {
            Serial.println("[RX] 잘못된 패킷 길이!");
            return;
        }

        uint8_t* pkt = (uint8_t*)rxData.data();

        // 헤더 검증: 0xAA 0x55
        if (pkt[0] != 0xAA || pkt[1] != 0x55) {
            Serial.println("[RX] 헤더 불일치!");
            return;
        }

        // 체크섬 검증: Byte[2] ^ Byte[3] ^ Byte[4] ^ Byte[5] ^ Byte[6]
        uint8_t checksum = pkt[2] ^ pkt[3] ^ pkt[4] ^ pkt[5] ^ pkt[6];
        if (checksum != pkt[7]) {
            Serial.printf("[RX] 체크섬 오류! 계산=%02X, 수신=%02X\n", checksum, pkt[7]);
            return;
        }

        // 패킷 파싱
        uint8_t  commandId   = pkt[2];
        uint8_t  mode        = pkt[3];
        uint16_t periodMs    = pkt[4] | (pkt[5] << 8);  // Little Endian

        Serial.printf("[RX] CMD=0x%02X, Mode=%d, Period=%dms\n", commandId, mode, periodMs);

        // 명령 처리
        handleCommand(commandId, mode, periodMs);
    }
};

// ============================================================================
// [명령 처리 함수] - 새 명령 추가 시 여기에 case를 추가하세요!
// ============================================================================
void handleCommand(uint8_t cmd, uint8_t mode, uint16_t periodMs) {
    switch (cmd) {
        case CMD_START:
            // 타격 시작: 주기 설정 후 모터 구동 루프 활성화
            if (periodMs >= 200 && periodMs <= 2000) {
                g_strikePeriodMs = periodMs;
            }
            g_motorRunning = true;
            g_deviceState = STATE_RUNNING;
            Serial.printf("[CMD] START - 주기 %dms로 타격 시작\n", g_strikePeriodMs);
            break;

        case CMD_STOP:
            // 정상 정지
            g_motorRunning = false;
            g_motorPulseActive = false;
            digitalWrite(PIN_MOTOR_CONTROL, LOW);
            g_deviceState = STATE_IDLE;
            Serial.println("[CMD] STOP - 타격 정지");
            break;

        case CMD_EMERGENCY_STOP:
            // ★ 긴급 정지: 모든 출력 즉시 차단
            g_motorRunning = false;
            g_motorPulseActive = false;
            digitalWrite(PIN_MOTOR_CONTROL, LOW);
            g_deviceState = STATE_IDLE;
            Serial.println("[CMD] ⚠️ EMERGENCY STOP - 긴급 정지!");
            // [TODO] 추후 긴급정지 로그 저장, LED 점멸 등 추가 가능
            break;

        case CMD_CALIBRATE:
            // 캘리브레이션 모드: 모터 정지 + 센서 기준값 재설정
            g_motorRunning = false;
            g_motorPulseActive = false;
            digitalWrite(PIN_MOTOR_CONTROL, LOW);
            g_deviceState = STATE_CALIBRATING;
            Serial.println("[CMD] CALIBRATE - 캘리브레이션 모드 진입");
            // [TODO] 캘리브레이션 로직 구현
            // 예: 5초간 센서값 수집 → 평균을 기준값으로 설정
            // 완료 후 g_deviceState = STATE_IDLE; 로 복귀
            break;

        case CMD_SET_PERIOD:
            // 타격 주기만 변경 (동작 중에도 즉시 반영됨)
            if (periodMs >= 200 && periodMs <= 2000) {
                g_strikePeriodMs = periodMs;
                Serial.printf("[CMD] SET_PERIOD - 주기 %dms로 변경\n", periodMs);
            } else {
                Serial.println("[CMD] SET_PERIOD - 범위 초과! (200~2000ms)");
            }
            break;

        // =====================================================================
        // [TODO] 새 명령 추가 가이드:
        // =====================================================================
        // case CMD_SET_INTENSITY:  // 0x06
        //     // PWM 강도 조절 (0~255)
        //     // uint8_t intensity = mode;  // mode 바이트를 강도값으로 재활용
        //     // analogWrite(PIN_MOTOR_CONTROL, intensity);
        //     break;
        //
        // case CMD_ZONE_SELECT:    // 0x07
        //     // 특정 존 모터만 켜기/끄기
        //     // uint8_t zoneId = mode;
        //     // bool zoneOn = (periodMs & 0x01) != 0;
        //     // setZoneState(zoneId, zoneOn);
        //     break;
        // =====================================================================

        default:
            Serial.printf("[CMD] 알 수 없는 명령: 0x%02X\n", cmd);
            break;
    }
}

// ============================================================================
// [텔레메트리 패킷 생성 및 전송] - 12바이트 고정 패킷
// ============================================================================
void sendTelemetry() {
    if (!g_bleConnected || pTxCharacteristic == nullptr) return;

    uint8_t pkt[12];
    
    // 헤더
    pkt[0] = 0x55;  // Header 1
    pkt[1] = 0xAA;  // Header 2
    
    // 장치 상태
    pkt[2] = g_deviceState;
    
    // 흉부 압력값 (Little Endian)
    pkt[3] = (uint8_t)(g_filteredPressure & 0xFF);
    pkt[4] = (uint8_t)((g_filteredPressure >> 8) & 0xFF);
    
    // 호흡 위상 (Core 0에서 판별한 값)
    pkt[5] = g_respirationPhase;
    
    // 모터 활성 상태
    pkt[6] = g_motorPulseActive ? 0x01 : 0x00;
    
    // 전원 상태 (유선 전원이므로 항상 정상)
    pkt[7] = 0x64;  // 100% (0x64 = 100 decimal)
    
    // 현재 타격 주기 (Little Endian)
    pkt[8] = (uint8_t)(g_strikePeriodMs & 0xFF);
    pkt[9] = (uint8_t)((g_strikePeriodMs >> 8) & 0xFF);
    
    // 에러 코드
    pkt[10] = 0x00;  // [TODO] 에러 상황 감지 시 코드 설정
    
    // 체크섬: Byte[2] ~ Byte[10] XOR
    uint8_t checksum = 0;
    for (int i = 2; i <= 10; i++) {
        checksum ^= pkt[i];
    }
    pkt[11] = checksum;
    
    // BLE Notify 전송
    pTxCharacteristic->setValue(pkt, 12);
    pTxCharacteristic->notify();
}

// ============================================================================
// [Core 0 태스크] 호흡 주기 인지 전담 루프 (10ms = 100Hz 샘플링)
// ============================================================================
/*
 * ★★★ 이 함수가 호기/흡기 인지 알고리즘이 들어갈 자리입니다 ★★★
 * 
 * 현재 구현: 이동평균 필터 + 간단한 기울기 기반 위상 판별 (기본 틀)
 * 추후 할 일: 팀에서 받은 인지 알고리즘 코드를 아래 [ALGORITHM] 섹션에 교체
 * 
 * 데이터 흐름:
 *   ADC Raw값 → 이동평균 필터 → 기울기 계산 → 위상 판별 → g_respirationPhase에 저장
 *                                                            ↓
 *                                          Core 1이 읽어서 텔레메트리 패킷에 포함
 */
void respirationTask(void* parameter) {
    Serial.println("[Core 0] 호흡 인지 태스크 시작");
    
    int16_t prevFiltered = 0;  // 이전 필터값 (기울기 계산용)
    
    // [TUNE] 호기/흡기 판별 임계값 - 센서 특성에 따라 조정 필요
    const int16_t SLOPE_THRESHOLD_INHALE = 5;   // 이 이상 증가하면 흡기
    const int16_t SLOPE_THRESHOLD_EXHALE = -5;  // 이 이하로 감소하면 호기
    // ※ 보드와 센서를 받은 후, 시리얼 모니터로 실제 기울기값을 관찰하면서
    //   이 임계값을 조정하세요. 처음엔 ±10 정도로 시작 권장.

    for (;;) {  // 무한 루프 (FreeRTOS 태스크)
        // 1단계: ADC 읽기
        int16_t rawValue = analogRead(PIN_CHEST_SENSOR);
        
        // 2단계: 이동평균 필터 적용
        filterSum -= filterBuffer[filterIndex];
        filterBuffer[filterIndex] = rawValue;
        filterSum += rawValue;
        filterIndex = (filterIndex + 1) % FILTER_WINDOW_SIZE;
        
        int16_t filtered = (int16_t)(filterSum / FILTER_WINDOW_SIZE);
        g_filteredPressure = filtered;  // Core 1과 공유
        
        // =====================================================================
        // [ALGORITHM] ★ 호기/흡기 인지 알고리즘 - 이 부분을 교체하세요 ★
        // =====================================================================
        // 현재: 단순 기울기 비교 (데모용)
        // 추후: 팀에서 제공하는 알고리즘으로 교체
        //
        // 교체 방법:
        //   1. 아래 if-else 블록을 지우고
        //   2. 받은 알고리즘 함수를 여기에 붙여넣기
        //   3. 결과를 g_respirationPhase에 대입 (PHASE_INHALE / PHASE_EXHALE / PHASE_NONE)
        //
        // 입력으로 사용 가능한 변수:
        //   - rawValue: 현재 ADC 원시값 (0~4095)
        //   - filtered: 이동평균 필터 적용된 값
        //   - prevFiltered: 이전 루프의 필터값
        //   - (필요하면 전역 버퍼 filterBuffer[] 전체 접근 가능)
        //
        // 출력해야 할 것:
        //   - g_respirationPhase = PHASE_INHALE or PHASE_EXHALE or PHASE_NONE
        // =====================================================================
        
        int16_t slope = filtered - prevFiltered;
        
        if (slope >= SLOPE_THRESHOLD_INHALE) {
            g_respirationPhase = PHASE_INHALE;   // 압력 증가 = 흡기 (들숨)
        } else if (slope <= SLOPE_THRESHOLD_EXHALE) {
            g_respirationPhase = PHASE_EXHALE;   // 압력 감소 = 호기 (날숨)
        }
        // slope가 임계값 사이면 이전 상태 유지 (채터링 방지)
        
        prevFiltered = filtered;
        
        // =====================================================================
        // [ALGORITHM 끝]
        // =====================================================================
        
        // 10ms 대기 (100Hz 샘플링 주기)
        vTaskDelay(pdMS_TO_TICKS(10));
    }
}

// ============================================================================
// [Core 1 태스크] BLE 통신 + 모터 자체 주기 구동 + 텔레메트리 송신
// ============================================================================
/*
 * 이 태스크는 3가지 역할을 동시 수행:
 *   1. 모터를 설정된 주기(g_strikePeriodMs)로 펄스 구동
 *   2. 텔레메트리를 50ms마다 앱으로 Notify
 *   3. BLE 연결 상태 모니터링 (콜백에서 처리되므로 여기선 보조적)
 *
 * 모터 구동 방식:
 *   - g_motorRunning == true일 때만 동작
 *   - 주기의 절반: 모터 ON (타격)
 *   - 주기의 나머지 절반: 모터 OFF (복귀 대기)
 *   - [TUNE] 아래 MOTOR_ON_DURATION_RATIO를 조절해서 ON 시간 비율 변경 가능
 */

// [TUNE] 모터 ON 시간 = 전체 주기의 몇 %인지 (0.0 ~ 1.0)
// 예: 0.3 = 주기 500ms일 때 150ms ON + 350ms OFF
#define MOTOR_ON_DURATION_RATIO  0.3f

void systemTask(void* parameter) {
    Serial.println("[Core 1] 시스템 태스크 시작");
    
    unsigned long lastTelemetryTime = 0;
    unsigned long motorCycleStart = 0;
    bool motorPhaseOn = false;
    
    for (;;) {
        unsigned long now = millis();
        
        // ----- 모터 자체 주기 구동 로직 -----
        if (g_motorRunning) {
            uint16_t onDuration = (uint16_t)(g_strikePeriodMs * MOTOR_ON_DURATION_RATIO);
            uint16_t offDuration = g_strikePeriodMs - onDuration;
            
            if (!motorPhaseOn) {
                // OFF 상태에서 주기 도달 → ON 전환
                if (now - motorCycleStart >= offDuration) {
                    digitalWrite(PIN_MOTOR_CONTROL, HIGH);
                    g_motorPulseActive = true;
                    motorPhaseOn = true;
                    motorCycleStart = now;
                }
            } else {
                // ON 상태에서 ON 시간 경과 → OFF 전환
                if (now - motorCycleStart >= onDuration) {
                    digitalWrite(PIN_MOTOR_CONTROL, LOW);
                    g_motorPulseActive = false;
                    motorPhaseOn = false;
                    motorCycleStart = now;
                }
            }
        } else {
            // 구동 중지 상태면 항상 OFF 보장
            if (g_motorPulseActive) {
                digitalWrite(PIN_MOTOR_CONTROL, LOW);
                g_motorPulseActive = false;
            }
            motorPhaseOn = false;
            motorCycleStart = now;
        }
        
        // ----- 텔레메트리 주기 송신 -----
        if (now - lastTelemetryTime >= TELEMETRY_INTERVAL_MS) {
            sendTelemetry();
            lastTelemetryTime = now;
        }
        
        // 5ms 루프 (모터 타이밍 정밀도와 CPU 부하의 균형)
        vTaskDelay(pdMS_TO_TICKS(5));
    }
}

// ============================================================================
// [setup()] - 초기화 (Arduino 진입점, Core 1에서 실행)
// ============================================================================
void setup() {
    Serial.begin(115200);
    Serial.println("=== RespiSync ESP32 부팅 ===");

    // --- GPIO 초기화 ---
    pinMode(PIN_MOTOR_CONTROL, OUTPUT);
    digitalWrite(PIN_MOTOR_CONTROL, LOW);  // 시작 시 모터 OFF 보장
    // PIN_CHEST_SENSOR(34)는 입력 전용 핀이므로 pinMode 불필요

    // --- ADC 설정 ---
    analogReadResolution(12);     // 12비트 해상도 (0~4095)
    analogSetAttenuation(ADC_11db);  // 0~3.3V 전체 범위 읽기
    // [TUNE] 센서 전압 범위에 따라 감쇠 변경 가능:
    //   ADC_0db   = 0~1.1V
    //   ADC_2_5db = 0~1.5V  
    //   ADC_6db   = 0~2.2V
    //   ADC_11db  = 0~3.3V (기본값, 대부분의 센서에 적합)

    // --- NimBLE 초기화 ---
    Serial.println("[BLE] NimBLE 초기화...");
    NimBLEDevice::init("RespiSync_Vest");  // BLE 광고 이름 (앱 스캔 시 표시됨)
    // [TUNE] 이 이름이 앱의 DevicePairingScreen에서 "ESP32_Vest_BLE"로 
    //        표시됩니다. 앱 코드와 일치시키세요.
    
    NimBLEDevice::setPower(ESP_PWR_LVL_P9);  // 송신 출력 최대 (+9dBm)
    // [TUNE] 전력 절약이 필요하면 ESP_PWR_LVL_N0 등으로 낮출 수 있음

    // --- GATT Server 생성 ---
    NimBLEServer* pServer = NimBLEDevice::createServer();
    pServer->setCallbacks(new ServerCallbacks());

    // --- Service 생성 ---
    NimBLEService* pService = pServer->createService(SERVICE_UUID);

    // --- RX Characteristic (앱 → ESP32, Write) ---
    NimBLECharacteristic* pRxCharacteristic = pService->createCharacteristic(
        CHARACTERISTIC_UUID_RX,
        NIMBLE_PROPERTY::WRITE | NIMBLE_PROPERTY::WRITE_NR  // Write with/without response 둘 다 허용
    );
    pRxCharacteristic->setCallbacks(new RxCallbacks());

    // --- TX Characteristic (ESP32 → 앱, Notify) ---
    pTxCharacteristic = pService->createCharacteristic(
        CHARACTERISTIC_UUID_TX,
        NIMBLE_PROPERTY::NOTIFY
    );
    // NimBLE는 자동으로 CCCD(Client Characteristic Configuration Descriptor)를 추가함

    // --- Service 시작 ---
    pService->start();

    // --- BLE 광고 시작 ---
    NimBLEAdvertising* pAdvertising = NimBLEDevice::getAdvertising();
    pAdvertising->addServiceUUID(SERVICE_UUID);
    pAdvertising->setScanResponse(true);
    pAdvertising->start();
    Serial.println("[BLE] 광고 시작 - 앱에서 연결 대기 중...");

    // --- 듀얼코어 태스크 생성 ---
    // Core 0: 호흡 인지 (우선순위 1 = 낮음, 센서 읽기는 실시간성 덜 중요)
    xTaskCreatePinnedToCore(
        respirationTask,    // 태스크 함수
        "RespirationTask",  // 태스크 이름 (디버그용)
        4096,               // 스택 크기 (바이트) [TUNE] 부족 시 8192로 증가
        NULL,               // 매개변수
        1,                  // 우선순위 (1 = 기본)
        NULL,               // 태스크 핸들 (사용 안 함)
        0                   // ★ Core 0에 고정
    );

    // Core 1: 시스템 (BLE + 모터) - setup()이 이미 Core 1이므로 별도 태스크 생성
    xTaskCreatePinnedToCore(
        systemTask,         // 태스크 함수
        "SystemTask",       // 태스크 이름
        8192,               // 스택 크기 (BLE 사용으로 더 큰 스택 필요)
        NULL,               // 매개변수
        2,                  // 우선순위 (2 = 높음, BLE 응답성 확보)
        NULL,               // 태스크 핸들
        1                   // ★ Core 1에 고정
    );

    Serial.println("=== 듀얼코어 태스크 생성 완료 ===");
    Serial.printf("  Core 0: 호흡 인지 (100Hz)\n");
    Serial.printf("  Core 1: BLE + 모터 제어\n");
    Serial.println("================================");
}

// ============================================================================
// [loop()] - Arduino 기본 루프 (사용 안 함 - FreeRTOS 태스크가 대신 동작)
// ============================================================================
void loop() {
    // FreeRTOS 태스크가 모든 작업을 처리하므로 loop()는 비워둡니다.
    // CPU를 양보하기 위해 긴 딜레이 설정
    vTaskDelay(pdMS_TO_TICKS(1000));
}

// ============================================================================
// 
// ★★★ [개발 로드맵 & 다음 단계 가이드] ★★★
//
// ============================================================================
//
// 📌 1단계 (현재 완료): BLE 통신 확립
//    - NimBLE GATT Server 동작
//    - 8바이트 명령 수신 + 파싱
//    - 12바이트 텔레메트리 Notify 송신
//    - Fail-Safe (연결 끊김 시 모터 정지)
//
// 📌 2단계: 보드 수령 후 하드웨어 테스트
//    - 시리얼 모니터로 ADC 원시값 확인 (analogRead)
//    - 모터 핀 HIGH/LOW로 실제 모터 동작 확인
//    - 앱과 BLE 연결 → 패킷 송수신 확인
//
// 📌 3단계: 호흡 인지 알고리즘 교체
//    - 팀에서 받은 알고리즘 코드를 respirationTask() 내 [ALGORITHM] 섹션에 교체
//    - 입력: rawValue, filtered, prevFiltered
//    - 출력: g_respirationPhase (PHASE_INHALE / PHASE_EXHALE / PHASE_NONE)
//    - SLOPE_THRESHOLD 값을 실제 센서 데이터 보면서 튜닝
//
// 📌 4단계: 기능 확장
//    - 새 명령 추가: #define CMD_XXX + handleCommand()에 case 추가
//    - 강도 조절: PIN_MOTOR_CONTROL을 PWM 핀으로 변경 + analogWrite() 사용
//    - 다중 모터: 핀 배열 정의 + 존별 제어 로직 추가
//    - 캘리브레이션: 센서 기준값 EEPROM 저장
//
// 📌 5단계: 안정화
//    - Watchdog Timer 추가 (태스크 행 감지)
//    - 에러 코드 체계 확장 (텔레메트리 pkt[10])
//    - OTA 업데이트 (NimBLE와 별도 WiFi 스택 활용)
//
// ============================================================================

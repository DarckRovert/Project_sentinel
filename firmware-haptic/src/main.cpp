/**
 * Sentinel Haptic Firmware v0.1.0
 * Target: ESP32-S3 Mini
 *
 * Hardware:
 *   - 4x DRV2605L (Motor Driver, I2C) con multiplexor TCA9548A
 *   - 4x Motores LRA 10mm (o ERM según disponibilidad)
 *   - BLE 5.0 server vía NimBLE
 *
 * ## Protocolo BLE (1 byte):
 * ```
 * Bits [7:6] = Intensidad: 00=OFF, 01=LOW(33%), 10=MED(66%), 11=HIGH(100%)
 * Bits [5:4] = Zona:       00=FRONT, 01=BACK, 10=LEFT, 11=RIGHT
 * Bits [3:0] = Patrón:     0=single, 1=double, 2=continuous
 * ```
 *
 * ## Mapa de zonas a motores (ajustar según orientación del tapete):
 *   FRONT → Motor 0 (parte superior del tapete / espalda alta)
 *   BACK  → Motor 1 (parte inferior del tapete / zona lumbar)
 *   LEFT  → Motor 2 (lado izquierdo del tapete)
 *   RIGHT → Motor 3 (lado derecho del tapete)
 */

#include <Arduino.h>
#include <Wire.h>
#include <Adafruit_DRV2605.h>
#include <NimBLEDevice.h>

// ============================================================
//  CONFIGURACIÓN DE PINES
// ============================================================
#define I2C_SDA_PIN    8
#define I2C_SCL_PIN    9
#define MUX_ADDR       0x70  // TCA9548A I2C multiplexor

// ============================================================
//  BLE UUIDs (deben coincidir exactamente con HapticRepositoryImpl.kt)
// ============================================================
#define SERVICE_UUID        "550e8400-e29b-41d4-a716-446655440001"
#define CHARACTERISTIC_UUID "550e8400-e29b-41d4-a716-446655440002"
#define DEVICE_NAME         "SENTINEL_HAPTIC"

// ============================================================
//  DRIVERS DRV2605L
// ============================================================
static Adafruit_DRV2605 motors[4];
static bool motorOk[4] = {false, false, false, false};

// ============================================================
//  ESTADO GLOBAL
// ============================================================
static NimBLECharacteristic* pCharacteristic = nullptr;
static bool deviceConnected = false;

// ============================================================
//  EFECTOS HÁPTICOS (Waveform IDs de la librería DRV2605)
//  Ref: DRV2605L Datasheet, Table 1 — ROM Effect Library
// ============================================================
#define EFFECT_SINGLE_PULSE    1   // Strong Click 100%
#define EFFECT_DOUBLE_PULSE   14   // Double Click 80%
#define EFFECT_CONTINUOUS     47   // Buzz 1 - 20%

// ============================================================
//  MULTIPLEXOR I2C TCA9548A
// ============================================================
void selectMuxChannel(uint8_t channel) {
    if (channel > 3) return;
    Wire.beginTransmission(MUX_ADDR);
    Wire.write(1 << channel);
    Wire.endTransmission();
}

// ============================================================
//  CONTROL DE MOTORES
// ============================================================
void initMotors() {
    for (uint8_t i = 0; i < 4; i++) {
        selectMuxChannel(i);
        delay(10);
        if (motors[i].begin()) {
            motors[i].selectLibrary(1);  // Biblioteca ERM/LRA interna
            motors[i].setMode(DRV2605_MODE_INTTRIG);
            motorOk[i] = true;
            Serial.printf("Motor %d OK\n", i);
        } else {
            Serial.printf("Motor %d FALLO (verificar I2C)\n", i);
        }
    }
}

/**
 * Activa un motor con intensidad y patrón específicos.
 *
 * @param motorIndex  Índice del motor (0-3, mapeado a zona FRONT/BACK/LEFT/RIGHT)
 * @param intensity   0=OFF, 1=LOW, 2=MED, 3=HIGH
 * @param pattern     0=single, 1=double, 2=continuous
 */
void activateMotor(uint8_t motorIndex, uint8_t intensity, uint8_t pattern) {
    if (motorIndex > 3 || !motorOk[motorIndex]) return;

    selectMuxChannel(motorIndex);

    if (intensity == 0) {
        motors[motorIndex].stop();
        return;
    }

    // Seleccionar efecto según patrón
    uint8_t effect = EFFECT_SINGLE_PULSE;
    switch (pattern) {
        case 1: effect = EFFECT_DOUBLE_PULSE;  break;
        case 2: effect = EFFECT_CONTINUOUS;    break;
        default: effect = EFFECT_SINGLE_PULSE; break;
    }

    // Ajustar ganancia según intensidad (registro 0x17 del DRV2605L)
    // 0x7F = 50%, 0xBF = 75%, 0xFF = 100%
    uint8_t gain = 0;
    switch (intensity) {
        case 1: gain = 0x7F; break;  // LOW  33%
        case 2: gain = 0xBF; break;  // MED  75%
        case 3: gain = 0xFF; break;  // HIGH 100%
    }
    Wire.beginTransmission(0x5A);  // DRV2605L I2C addr
    Wire.write(0x17);              // Overdrive Voltage-Comp register
    Wire.write(gain);
    Wire.endTransmission();

    motors[motorIndex].setWaveform(0, effect);
    motors[motorIndex].setWaveform(1, 0);  // Fin de secuencia
    motors[motorIndex].go();
}

void stopAllMotors() {
    for (uint8_t i = 0; i < 4; i++) {
        if (motorOk[i]) {
            selectMuxChannel(i);
            motors[i].stop();
        }
    }
}

// ============================================================
//  PROCESAMIENTO DEL COMANDO BLE (1 byte)
// ============================================================
void processHapticCommand(uint8_t cmd) {
    uint8_t intensity = (cmd >> 6) & 0x03;  // Bits [7:6]
    uint8_t zone      = (cmd >> 4) & 0x03;  // Bits [5:4]
    uint8_t pattern   = cmd & 0x0F;          // Bits [3:0]

    Serial.printf("CMD: 0x%02X → Intensidad=%d Zona=%d Patrón=%d\n",
                  cmd, intensity, zone, pattern);

    if (intensity == 0) {
        stopAllMotors();
        return;
    }

    // Mapear zona al índice de motor
    // Zona 0=FRONT→Motor0, 1=BACK→Motor1, 2=LEFT→Motor2, 3=RIGHT→Motor3
    activateMotor(zone, intensity, pattern);
}

// ============================================================
//  BLE CALLBACKS
// ============================================================
class SentinelServerCallbacks : public NimBLEServerCallbacks {
    void onConnect(NimBLEServer* pServer) override {
        deviceConnected = true;
        Serial.println("BLE: Android conectado");
    }
    void onDisconnect(NimBLEServer* pServer) override {
        deviceConnected = false;
        stopAllMotors();
        Serial.println("BLE: Android desconectado — reiniciando publicidad");
        NimBLEDevice::startAdvertising();
    }
};

class HapticCharCallbacks : public NimBLECharacteristicCallbacks {
    void onWrite(NimBLECharacteristic* pChar) override {
        std::string value = pChar->getValue();
        if (!value.empty()) {
            processHapticCommand(static_cast<uint8_t>(value[0]));
        }
    }
};

// ============================================================
//  SETUP
// ============================================================
void setup() {
    Serial.begin(115200);
    Serial.println("\n=== Sentinel Haptic Firmware v0.1.0 ===");

    // I2C
    Wire.begin(I2C_SDA_PIN, I2C_SCL_PIN);
    Wire.setClock(400000);  // Fast mode

    // Inicializar motores
    initMotors();

    // BLE
    NimBLEDevice::init(DEVICE_NAME);
    NimBLEDevice::setPower(ESP_PWR_LVL_P9);  // Máxima potencia para alcance vehicular

    NimBLEServer* pServer = NimBLEDevice::createServer();
    pServer->setCallbacks(new SentinelServerCallbacks());

    NimBLEService* pService = pServer->createService(SERVICE_UUID);

    pCharacteristic = pService->createCharacteristic(
        CHARACTERISTIC_UUID,
        NIMBLE_PROPERTY::WRITE_NR  // Write Without Response (mínima latencia)
    );
    pCharacteristic->setCallbacks(new HapticCharCallbacks());

    pService->start();

    NimBLEAdvertising* pAdvertising = NimBLEDevice::getAdvertising();
    pAdvertising->addServiceUUID(SERVICE_UUID);
    pAdvertising->setScanResponse(true);
    NimBLEDevice::startAdvertising();

    Serial.println("BLE: Publicando como '" DEVICE_NAME "'");
    Serial.println("Esperando conexión del retrovisor Android...");
}

// ============================================================
//  LOOP
// ============================================================
void loop() {
    // El firmware es completamente orientado a eventos (callbacks BLE)
    // El loop principal solo mantiene el watchdog y monitoreo de estado
    delay(100);
}

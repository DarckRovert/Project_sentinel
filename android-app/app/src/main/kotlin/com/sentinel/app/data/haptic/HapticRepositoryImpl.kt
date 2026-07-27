package com.sentinel.app.data.haptic

import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import com.sentinel.app.domain.model.CollisionEvent
import com.sentinel.app.domain.model.ThreatLevel
import com.sentinel.app.domain.model.ThreatZone
import com.sentinel.app.domain.repository.HapticRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Implementación BLE GATT del repositorio háptico para el ESP32 del tapete de asiento.
 *
 * ## UUIDs del servicio GATT personalizado (deben coincidir con el firmware ESP32):
 * - Service:        550e8400-e29b-41d4-a716-446655440001
 * - Characteristic: 550e8400-e29b-41d4-a716-446655440002  (WRITE_NO_RESPONSE)
 *
 * ## Protocolo de comando (1 byte):
 * ```
 * Bits [7:6] → Intensidad:  00=OFF, 01=LOW(33%), 10=MED(66%), 11=HIGH(100%)
 * Bits [5:4] → Zona:        00=FRONT, 01=BACK, 10=LEFT, 11=RIGHT
 * Bits [3:0] → Patrón:      0000=single pulse, 0001=double, 0010=continuous
 * ```
 */
@Singleton
class HapticRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : HapticRepository {

    companion object {
        private const val DEVICE_NAME = "SENTINEL_HAPTIC"
        private const val SCAN_TIMEOUT_MS = 10_000L
        private val SERVICE_UUID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001")
        private val CHAR_UUID    = UUID.fromString("550e8400-e29b-41d4-a716-446655440002")
    }

    private var gatt: BluetoothGatt? = null
    private var alertCharacteristic: BluetoothGattCharacteristic? = null
    private val _connectionState = MutableStateFlow(false)

    override val isConnected: Boolean get() = _connectionState.value
    override val connectionState: Flow<Boolean> = _connectionState.asStateFlow()

    override suspend fun connect() {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter
            ?: throw IllegalStateException("Bluetooth no disponible en este dispositivo")

        val device = scanForDevice(adapter.bluetoothLeScanner)
        connectGatt(device)
    }

    private suspend fun scanForDevice(scanner: BluetoothLeScanner): BluetoothDevice =
        suspendCancellableCoroutine { cont ->
            var scanCallback: ScanCallback? = null
            scanCallback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    if (result.device.name == DEVICE_NAME) {
                        scanner.stopScan(this)
                        Timber.i("HapticRepo: Dispositivo háptico encontrado: ${result.device.address}")
                        cont.resume(result.device)
                    }
                }
                override fun onScanFailed(errorCode: Int) {
                    cont.resumeWithException(RuntimeException("BLE Scan falló con código: $errorCode"))
                }
            }
            scanner.startScan(scanCallback)
            cont.invokeOnCancellation { scanner.stopScan(scanCallback) }
        }

    private suspend fun connectGatt(device: BluetoothDevice): Unit =
        suspendCancellableCoroutine { cont ->
            val callback = object : BluetoothGattCallback() {
                override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                    when (newState) {
                        BluetoothProfile.STATE_CONNECTED -> {
                            Timber.i("HapticRepo: GATT conectado — descubriendo servicios")
                            g.discoverServices()
                        }
                        BluetoothProfile.STATE_DISCONNECTED -> {
                            _connectionState.value = false
                            Timber.w("HapticRepo: GATT desconectado")
                        }
                    }
                }

                override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        alertCharacteristic = g.getService(SERVICE_UUID)
                            ?.getCharacteristic(CHAR_UUID)

                        if (alertCharacteristic != null) {
                            gatt = g
                            _connectionState.value = true
                            Timber.i("HapticRepo: Característica GATT lista")
                            cont.resume(Unit)
                        } else {
                            g.disconnect()
                            cont.resumeWithException(
                                IllegalStateException("Característica háptica no encontrada en el ESP32")
                            )
                        }
                    } else {
                        cont.resumeWithException(RuntimeException("onServicesDiscovered status: $status"))
                    }
                }
            }
            gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            cont.invokeOnCancellation { gatt?.disconnect() }
        }

    override suspend fun sendAlert(event: CollisionEvent) {
        val char = alertCharacteristic ?: return
        val command = buildCommand(event)
        char.value = byteArrayOf(command)
        char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        gatt?.writeCharacteristic(char)
        Timber.d("HapticRepo: Comando enviado 0x${command.toString(16).uppercase()} → Zona=${event.threatZone} Nivel=${event.threatLevel}")
    }

    override suspend fun stopVibration() {
        val char = alertCharacteristic ?: return
        char.value = byteArrayOf(0x00)  // Todo en cero = apagar vibración
        char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        gatt?.writeCharacteristic(char)
    }

    override suspend fun disconnect() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        alertCharacteristic = null
        _connectionState.value = false
    }

    /**
     * Construye el byte de comando a partir del evento de colisión.
     *
     * ```
     * Bits [7:6] = Intensidad (basada en ThreatLevel)
     * Bits [5:4] = Zona       (basada en ThreatZone)
     * Bits [3:0] = Patrón     (basado en ThreatLevel)
     * ```
     */
    private fun buildCommand(event: CollisionEvent): Byte {
        val intensity = when (event.threatLevel) {
            ThreatLevel.CRITICAL -> 0b11
            ThreatLevel.WARNING  -> 0b10
            ThreatLevel.CAUTION  -> 0b01
            ThreatLevel.NONE     -> 0b00
        }
        val zone = event.threatZone.bleCode

        val pattern = when (event.threatLevel) {
            ThreatLevel.CRITICAL -> 0b0010  // Continuo
            ThreatLevel.WARNING  -> 0b0001  // Doble pulso
            ThreatLevel.CAUTION  -> 0b0000  // Pulso único
            ThreatLevel.NONE     -> 0b0000
        }

        return ((intensity shl 6) or (zone shl 4) or pattern).toByte()
    }
}

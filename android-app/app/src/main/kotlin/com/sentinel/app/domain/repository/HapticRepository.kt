package com.sentinel.app.domain.repository

import com.sentinel.app.domain.model.CollisionEvent
import kotlinx.coroutines.flow.Flow

/**
 * Contrato del repositorio háptico.
 * La implementación concreta ([HapticRepositoryImpl]) gestiona la
 * conexión BLE GATT con el ESP32 del tapete de asiento.
 */
interface HapticRepository {

    /** Estado de conexión BLE con el ESP32. */
    val isConnected: Boolean

    /** Flow del estado de conexión. */
    val connectionState: Flow<Boolean>

    /**
     * Escanea y conecta al dispositivo háptico ESP32 (nombre BLE: "SENTINEL_HAPTIC").
     * Lanza excepción si no se encuentra en 10 segundos.
     */
    suspend fun connect()

    /** Desconecta el GATT de manera segura. */
    suspend fun disconnect()

    /**
     * Envía un comando háptico al tapete de asiento.
     *
     * Protocolo: 1 byte
     * ```
     * Bits [7:6] → Intensidad  (00=OFF, 01=LOW, 10=MED, 11=HIGH)
     * Bits [5:4] → Zona        (00=FRONT, 01=BACK, 10=LEFT, 11=RIGHT)
     * Bits [3:0] → Patrón      (0=single, 1=double, 2=continuous)
     * ```
     *
     * @param event [CollisionEvent] del que se deriva el comando.
     */
    suspend fun sendAlert(event: CollisionEvent)

    /**
     * Detiene toda la vibración del tapete (emergencia o fin de amenaza).
     */
    suspend fun stopVibration()
}

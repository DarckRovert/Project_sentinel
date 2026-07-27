package com.sentinel.app.domain.repository

import com.sentinel.app.domain.model.RadarTarget
import kotlinx.coroutines.flow.Flow

/**
 * Contrato del repositorio del radar.
 * La implementación concreta ([RadarRepositoryImpl]) maneja la
 * comunicación USB-Serial con el HLK-LD2450.
 */
interface RadarRepository {

    /**
     * Flow continuo de frames del radar.
     * Cada emisión contiene la lista de targets detectados en ese frame
     * (máximo 3 objetos simultáneos en el LD2450).
     *
     * El Flow termina cuando se desconecta el dispositivo USB.
     */
    fun radarTargetsFlow(): Flow<List<RadarTarget>>

    /** True si el radar está conectado y respondiendo. */
    val isConnected: Boolean

    /** Inicializa la conexión USB-Serial con el radar. Lanza excepción si falla. */
    suspend fun connect()

    /** Cierra la conexión USB-Serial de manera segura. */
    suspend fun disconnect()
}

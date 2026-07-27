package com.sentinel.app.data.radar

import com.sentinel.app.domain.model.RadarTarget
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Parser del protocolo binario del radar HLK-LD2450.
 *
 * ## Formato de trama (30 bytes total):
 *
 * ```
 * [AA FF 03 00]          — Header fijo (4 bytes)
 * [T1_X_L T1_X_H]       — Target 1: X position (signed int16, little-endian, mm)
 * [T1_Y_L T1_Y_H]       — Target 1: Y position (signed int16, little-endian, mm)
 * [T1_S_L T1_S_H]       — Target 1: Speed (signed int16, little-endian, cm/s)
 * [T1_D_L T1_D_H]       — Target 1: Distance resolution (uint16)
 * ... (Target 2) ...     — 8 bytes
 * ... (Target 3) ...     — 8 bytes
 * [55 CC]                — Footer fijo (2 bytes)
 * ```
 *
 * Referencia: HLK-LD2450 User Manual v1.2 — Section 3.2 Protocol Format
 *
 * NOTA: Y positivo = frente del vehículo. X positivo = lado derecho.
 * La velocidad NEGATIVA indica que el objeto SE ACERCA al sensor.
 */
class Ld2450FrameParser {

    companion object {
        private const val FRAME_HEADER_SIZE = 4
        private const val FRAME_FOOTER_SIZE = 2
        private const val TARGET_DATA_SIZE = 8      // bytes por target
        private const val MAX_TARGETS = 3
        private const val TOTAL_FRAME_SIZE = FRAME_HEADER_SIZE + (TARGET_DATA_SIZE * MAX_TARGETS) + FRAME_FOOTER_SIZE // 30 bytes

        private val FRAME_HEADER = byteArrayOf(0xAA.toByte(), 0xFF.toByte(), 0x03.toByte(), 0x00.toByte())
        private val FRAME_FOOTER = byteArrayOf(0x55.toByte(), 0xCC.toByte())
    }

    /**
     * Buffer acumulador para manejar fragmentación de tramas USB-Serial.
     * El LD2450 envía frames a ~10Hz; el buffer nunca debería superar 300 bytes.
     */
    private val accumulator = ArrayDeque<Byte>(256)

    /**
     * Agrega bytes recibidos del puerto serial al buffer acumulador
     * e intenta extraer frames completos.
     *
     * @param rawBytes  Bytes crudos recibidos del USB-Serial.
     * @return          Lista de listas de [RadarTarget] (una por frame completo encontrado).
     */
    fun feed(rawBytes: ByteArray, timestamp: Long = System.currentTimeMillis()): List<List<RadarTarget>> {
        accumulator.addAll(rawBytes.toList())

        val results = mutableListOf<List<RadarTarget>>()

        while (accumulator.size >= TOTAL_FRAME_SIZE) {
            val headerIndex = findHeader()
            if (headerIndex == -1) {
                // No hay header — descartar bytes hasta limpiar buffer
                if (accumulator.size > TOTAL_FRAME_SIZE) {
                    repeat(accumulator.size - TOTAL_FRAME_SIZE + 1) { accumulator.removeFirst() }
                }
                break
            }

            if (headerIndex > 0) {
                // Descartar bytes anteriores al header (basura del inicio)
                repeat(headerIndex) { accumulator.removeFirst() }
            }

            if (accumulator.size < TOTAL_FRAME_SIZE) break  // Frame incompleto — esperar más datos

            val frame = ByteArray(TOTAL_FRAME_SIZE) { accumulator[it] }

            // Validar footer
            if (frame[TOTAL_FRAME_SIZE - 2] != FRAME_FOOTER[0] ||
                frame[TOTAL_FRAME_SIZE - 1] != FRAME_FOOTER[1]) {
                Timber.w("Ld2450Parser: Footer inválido — descartando frame")
                accumulator.removeFirst()  // Avanzar un byte y reintentar
                continue
            }

            // Frame válido → parsear targets
            val targets = parseTargets(frame, timestamp)
            results.add(targets)

            // Consumir el frame del acumulador
            repeat(TOTAL_FRAME_SIZE) { accumulator.removeFirst() }
        }

        return results
    }

    /**
     * Parsea los 3 targets del frame.
     * Un target con todos los campos en 0 se considera "sin objeto".
     */
    private fun parseTargets(frame: ByteArray, timestamp: Long): List<RadarTarget> {
        val targets = mutableListOf<RadarTarget>()

        for (i in 0 until MAX_TARGETS) {
            val offset = FRAME_HEADER_SIZE + (i * TARGET_DATA_SIZE)
            val buf = ByteBuffer.wrap(frame, offset, TARGET_DATA_SIZE).order(ByteOrder.LITTLE_ENDIAN)

            val x = buf.short.toInt()     // signed int16
            val y = buf.short.toInt()     // signed int16
            val speed = buf.short.toInt() // signed int16
            val distRes = buf.short.toInt() and 0xFFFF  // uint16

            // Filtrar targets vacíos (todos ceros = no hay objeto en esa ranura)
            if (x == 0 && y == 0 && speed == 0) continue

            targets.add(
                RadarTarget(
                    id = i,
                    x = x,
                    y = y,
                    speed = speed,
                    distanceRes = distRes,
                    timestamp = timestamp
                )
            )
        }

        return targets
    }

    /** Busca el índice del header 0xAA FF 03 00 en el acumulador. */
    private fun findHeader(): Int {
        val size = accumulator.size
        for (i in 0..(size - FRAME_HEADER_SIZE)) {
            if (accumulator[i] == FRAME_HEADER[0] &&
                accumulator[i + 1] == FRAME_HEADER[1] &&
                accumulator[i + 2] == FRAME_HEADER[2] &&
                accumulator[i + 3] == FRAME_HEADER[3]) {
                return i
            }
        }
        return -1
    }

    /** Limpia el buffer acumulador (usar al desconectar el radar). */
    fun reset() = accumulator.clear()
}

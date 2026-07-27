package com.sentinel.app.domain.model

/**
 * Nivel de amenaza de colisión calculado por el TTC Engine.
 */
enum class ThreatLevel {
    /** Sin amenaza detectada o TTC > 6 segundos. */
    NONE,
    /** TTC entre 4–6 segundos — aviso informativo. */
    CAUTION,
    /** TTC entre 2–4 segundos — advertencia activa. */
    WARNING,
    /** TTC < 2 segundos — alerta crítica, activar háptico máximo. */
    CRITICAL
}

/**
 * Evento de colisión evaluado por [CollisionDetectionUseCase].
 *
 * @param threatLevel    Nivel de urgencia calculado.
 * @param ttcSeconds     Time To Collision en segundos. MAX_VALUE = sin colisión proyectada.
 * @param sourceTarget   Target del radar que generó la amenaza.
 * @param threatZone     Zona direccional del peligro para el módulo háptico.
 * @param timestamp      Timestamp del evento.
 */
data class CollisionEvent(
    val threatLevel: ThreatLevel,
    val ttcSeconds: Float,
    val sourceTarget: RadarTarget,
    val threatZone: ThreatZone,
    val timestamp: Long = System.currentTimeMillis()
) {
    val isActionable: Boolean
        get() = threatLevel != ThreatLevel.NONE
}

/**
 * Zona direccional de la amenaza para mapeo al tapete háptico.
 * Los valores corresponden al byte de comando BLE (bits [5:4]).
 */
enum class ThreatZone(val bleCode: Int) {
    FRONT(0b00),
    BACK(0b01),
    LEFT(0b10),
    RIGHT(0b11);

    companion object {
        /**
         * Determina la zona a partir del ángulo de llegada del radar target.
         * @param angleDegrees Ángulo en grados desde el frente (-180 a +180).
         */
        fun fromAngle(angleDegrees: Float): ThreatZone = when {
            angleDegrees in -45f..45f   -> FRONT
            angleDegrees > 135f
                    || angleDegrees < -135f -> BACK
            angleDegrees in 45f..135f   -> RIGHT
            else                         -> LEFT  // -135 a -45
        }
    }
}

package com.sentinel.app.domain.model

/**
 * Representa un único objeto detectado por el radar HLK-LD2450.
 *
 * @param id          Índice del target en el frame del radar (0–2 para LD2450).
 * @param x           Posición X en mm (positivo = derecha del vehículo).
 * @param y           Posición Y en mm (positivo = frente del vehículo).
 * @param speed       Velocidad relativa en cm/s (negativo = acercándose).
 * @param distanceRes Resolución de distancia en mm.
 * @param timestamp   Timestamp del frame en ms (System.currentTimeMillis).
 */
data class RadarTarget(
    val id: Int,
    val x: Int,           // mm
    val y: Int,           // mm
    val speed: Int,       // cm/s — negativo = objeto se acerca
    val distanceRes: Int, // mm
    val timestamp: Long
) {
    /** Distancia euclidiana al sensor en metros. */
    val distanceMeters: Float
        get() = kotlin.math.sqrt((x * x + y * y).toDouble()).toFloat() / 1000f

    /** Velocidad relativa en m/s (normalizada). */
    val speedMps: Float
        get() = speed / 100f

    /** True si el objeto se está acercando a nuestro vehículo. */
    val isApproaching: Boolean
        get() = speed < -10  // umbral de -0.1 m/s para filtrar ruido estático

    /** Ángulo de llegada en grados desde el frente (0° = directo al frente). */
    val angleDegrees: Float
        get() = Math.toDegrees(kotlin.math.atan2(x.toDouble(), y.toDouble())).toFloat()
}

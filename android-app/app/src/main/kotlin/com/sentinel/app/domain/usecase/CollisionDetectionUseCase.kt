package com.sentinel.app.domain.usecase

import com.sentinel.app.domain.model.CollisionEvent
import com.sentinel.app.domain.model.RadarTarget
import com.sentinel.app.domain.model.SentinelConfig
import com.sentinel.app.domain.model.ThreatLevel
import com.sentinel.app.domain.model.ThreatZone
import com.sentinel.app.domain.repository.RadarRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import javax.inject.Inject

/**
 * Use Case principal de Sentinel.
 *
 * Procesa el stream de [RadarTarget] y emite [CollisionEvent] cuando
 * se detecta una amenaza de colisión.
 *
 * ## Algoritmo TTC (Time To Collision):
 * ```
 * TTC = -distance / relative_velocity
 * ```
 * La velocidad relativa es negativa cuando el objeto se acerca.
 * Si la velocidad es >= -0.1 m/s (mínimo filtro de ruido), el TTC es infinito.
 *
 * ## Multi-target:
 * Para frames con múltiples targets, se emite el evento del target
 * con el TTC más bajo (amenaza más inmediata).
 */
class CollisionDetectionUseCase @Inject constructor(
    private val radarRepository: RadarRepository
) {
    /**
     * Flow de eventos de colisión filtrados y clasificados.
     * Solo emite cuando hay al menos una amenaza (ThreatLevel != NONE).
     *
     * @param config Configuración de umbrales activa.
     */
    fun observe(config: SentinelConfig): Flow<CollisionEvent> {
        return radarRepository.radarTargetsFlow()
            .mapNotNull { targets -> evaluateFrame(targets, config) }
    }

    /**
     * Evalúa un frame de targets y retorna el evento de mayor urgencia.
     * Retorna null si no hay amenaza activa.
     */
    internal fun evaluateFrame(
        targets: List<RadarTarget>,
        config: SentinelConfig
    ): CollisionEvent? {
        if (targets.isEmpty()) return null

        val candidates = targets
            .filter { it.isApproaching }
            .filter { it.distanceMeters <= config.maxDetectionRange }
            .filter { kotlin.math.abs(it.speedMps) >= config.minSpeedFilter }

        if (candidates.isEmpty()) return null

        // Seleccionar el target con el TTC más bajo (amenaza más inmediata)
        val mostThreatening = candidates.minByOrNull { calculateTTC(it) } ?: return null
        val ttc = calculateTTC(mostThreatening)
        val level = classifyThreatLevel(ttc, config)

        if (level == ThreatLevel.NONE) return null

        return CollisionEvent(
            threatLevel = level,
            ttcSeconds = ttc,
            sourceTarget = mostThreatening,
            threatZone = ThreatZone.fromAngle(mostThreatening.angleDegrees)
        )
    }

    /**
     * Calcula el Time To Collision en segundos.
     *
     * @return TTC en segundos o [Float.MAX_VALUE] si no hay colisión proyectada.
     */
    internal fun calculateTTC(target: RadarTarget): Float {
        val speed = target.speedMps
        // Solo calcular si el objeto se acerca de forma significativa
        return if (speed < -0.1f) {
            -target.distanceMeters / speed
        } else {
            Float.MAX_VALUE
        }
    }

    private fun classifyThreatLevel(ttcSeconds: Float, config: SentinelConfig): ThreatLevel = when {
        ttcSeconds <= config.ttcCriticalSeconds -> ThreatLevel.CRITICAL
        ttcSeconds <= config.ttcWarningSeconds  -> ThreatLevel.WARNING
        ttcSeconds <= config.ttcCautionSeconds  -> ThreatLevel.CAUTION
        else                                    -> ThreatLevel.NONE
    }
}

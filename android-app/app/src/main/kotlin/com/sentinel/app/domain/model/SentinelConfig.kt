package com.sentinel.app.domain.model

/**
 * Configuración de umbrales del sistema Sentinel.
 * Persiste en DataStore y permite ajuste por perfil de conductor.
 *
 * @param ttcCriticalSeconds  TTC en segundos para nivel CRITICAL. Default: 2.0s
 * @param ttcWarningSeconds   TTC en segundos para nivel WARNING.  Default: 4.0s
 * @param ttcCautionSeconds   TTC en segundos para nivel CAUTION.  Default: 6.0s
 * @param minSpeedFilter      Velocidad mínima relativa (m/s) para considerar un target.
 *                            Filtra objetos estáticos. Default: 0.5 m/s
 * @param maxDetectionRange   Rango máximo de detección en metros. Default: 15m (LD2450 real)
 * @param hapticEnabled       Si el módulo háptico está activo. Default: false (Kit Esencial)
 * @param audioAlertsEnabled  Si las alertas de audio están activas. Default: true
 */
data class SentinelConfig(
    val ttcCriticalSeconds: Float = 2.0f,
    val ttcWarningSeconds: Float = 4.0f,
    val ttcCautionSeconds: Float = 6.0f,
    val minSpeedFilter: Float = 0.5f,
    val maxDetectionRange: Float = 15f,
    val hapticEnabled: Boolean = false,
    val audioAlertsEnabled: Boolean = true
)

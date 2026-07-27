package com.sentinel.app.core.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.sentinel.app.domain.model.CollisionEvent
import com.sentinel.app.domain.model.SentinelConfig
import com.sentinel.app.domain.model.ThreatLevel
import com.sentinel.app.domain.repository.HapticRepository
import com.sentinel.app.domain.repository.RadarRepository
import com.sentinel.app.domain.usecase.CollisionDetectionUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import timber.log.Timber
import javax.inject.Inject

/**
 * Servicio en primer plano de Sentinel — el motor central del sistema.
 *
 * Se ejecuta como [Service] de Android con `foregroundServiceType=connectedDevice`
 * para garantizar que el sistema operativo NO lo mate mientras el vehículo circula.
 *
 * ## Ciclo de vida:
 * 1. `onCreate()` → Conectar radar (USB), conectar tapete háptico (BLE si está habilitado)
 * 2. Coroutine de supervisión → Observar [CollisionDetectionUseCase.observe]
 * 3. Por cada [CollisionEvent] → Disparar alertas (audio, visual vía broadcast, háptico)
 * 4. `onDestroy()` → Desconectar sensores, cancelar coroutines
 *
 * ## Comunicación con la UI:
 * Envía broadcasts locales con [ACTION_COLLISION_EVENT] que el [MainViewModel] observa.
 */
@AndroidEntryPoint
class SentinelForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "sentinel_service_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_COLLISION_EVENT = "com.sentinel.COLLISION_EVENT"
        const val EXTRA_THREAT_LEVEL = "threat_level"
        const val EXTRA_TTC_SECONDS = "ttc_seconds"
        const val EXTRA_THREAT_ZONE = "threat_zone"
    }

    @Inject lateinit var radarRepository: RadarRepository
    @Inject lateinit var hapticRepository: HapticRepository
    @Inject lateinit var collisionDetectionUseCase: CollisionDetectionUseCase

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val config = SentinelConfig()  // TODO: Cargar desde DataStore

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Sentinel activo — Monitoreando..."))
        Timber.i("SentinelForegroundService: Iniciado")
        startSensorPipeline()
    }

    private fun startSensorPipeline() {
        serviceScope.launch {
            // 1. Conectar radar
            try {
                radarRepository.connect()
                Timber.i("SentinelForegroundService: Radar conectado")
            } catch (e: Exception) {
                Timber.e(e, "SentinelForegroundService: Error conectando radar")
                updateNotification("⚠️ Radar no detectado — Verificar USB OTG")
                return@launch
            }

            // 2. Conectar tapete háptico (solo Kit Premium)
            if (config.hapticEnabled) {
                try {
                    hapticRepository.connect()
                    Timber.i("SentinelForegroundService: Tapete háptico conectado")
                } catch (e: Exception) {
                    Timber.w(e, "SentinelForegroundService: Tapete háptico no disponible — continuando sin háptico")
                }
            }

            // 3. Pipeline principal de detección
            collisionDetectionUseCase.observe(config)
                .collect { event ->
                    handleCollisionEvent(event)
                }
        }
    }

    private suspend fun handleCollisionEvent(event: CollisionEvent) {
        Timber.w("COLISION: Nivel=${event.threatLevel} TTC=${String.format("%.1f", event.ttcSeconds)}s Zona=${event.threatZone}")

        // Broadcast a la UI
        sendBroadcast(Intent(ACTION_COLLISION_EVENT).apply {
            putExtra(EXTRA_THREAT_LEVEL, event.threatLevel.name)
            putExtra(EXTRA_TTC_SECONDS, event.ttcSeconds)
            putExtra(EXTRA_THREAT_ZONE, event.threatZone.name)
        })

        // Alerta háptica (si está conectado el tapete)
        if (config.hapticEnabled && hapticRepository.isConnected) {
            hapticRepository.sendAlert(event)
        }

        // Actualizar notificación con el estado actual
        val msg = when (event.threatLevel) {
            ThreatLevel.CRITICAL -> "🔴 PELIGRO — Impacto en ${String.format("%.1f", event.ttcSeconds)}s"
            ThreatLevel.WARNING  -> "🟡 ADVERTENCIA — ${String.format("%.1f", event.ttcSeconds)}s"
            ThreatLevel.CAUTION  -> "🔵 PRECAUCIÓN — ${event.threatZone.name}"
            ThreatLevel.NONE     -> "Sentinel activo — Monitoreando..."
        }
        updateNotification(msg)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.launch {
            radarRepository.disconnect()
            hapticRepository.disconnect()
        }
        serviceScope.cancel()
        Timber.i("SentinelForegroundService: Detenido")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ----------------------------------------------------------------
    // Notificación persistente (requerida para Foreground Service)
    // ----------------------------------------------------------------

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Sentinel — Monitor de Colisiones",
            NotificationManager.IMPORTANCE_LOW  // LOW para no interrumpir al conductor con el sonido del sistema
        ).apply {
            description = "Estado del sistema de alertas vehiculares Sentinel"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(content: String): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("🛡️ Sentinel")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()

    private fun updateNotification(content: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(content))
    }
}

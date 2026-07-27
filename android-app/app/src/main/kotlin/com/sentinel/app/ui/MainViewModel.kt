package com.sentinel.app.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sentinel.app.core.service.SentinelForegroundService
import com.sentinel.app.domain.model.ThreatLevel
import com.sentinel.app.domain.model.ThreatZone
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel del HUD de Sentinel.
 * Recibe broadcasts del [SentinelForegroundService] y expone
 * [UiState] al Composable del HUD.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    data class UiState(
        val threatLevel: ThreatLevel = ThreatLevel.NONE,
        val ttcSeconds: Float = Float.MAX_VALUE,
        val threatZone: ThreatZone = ThreatZone.FRONT,
        val isRadarConnected: Boolean = false,
        val isHapticConnected: Boolean = false
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val collisionReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val level = ThreatLevel.valueOf(
                intent.getStringExtra(SentinelForegroundService.EXTRA_THREAT_LEVEL) ?: "NONE"
            )
            val ttc = intent.getFloatExtra(SentinelForegroundService.EXTRA_TTC_SECONDS, Float.MAX_VALUE)
            val zone = ThreatZone.valueOf(
                intent.getStringExtra(SentinelForegroundService.EXTRA_THREAT_ZONE) ?: "FRONT"
            )
            _uiState.update { it.copy(threatLevel = level, ttcSeconds = ttc, threatZone = zone) }

            // Auto-clear después de 3 segundos si no llega otro evento
            viewModelScope.launch {
                kotlinx.coroutines.delay(3_000)
                _uiState.update { current ->
                    if (current.threatLevel == level) current.copy(threatLevel = ThreatLevel.NONE)
                    else current
                }
            }
        }
    }

    init {
        context.registerReceiver(
            collisionReceiver,
            IntentFilter(SentinelForegroundService.ACTION_COLLISION_EVENT),
            Context.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onCleared() {
        super.onCleared()
        try { context.unregisterReceiver(collisionReceiver) } catch (_: Exception) {}
    }
}

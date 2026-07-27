package com.sentinel.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sentinel.app.domain.model.ThreatLevel
import com.sentinel.app.domain.model.ThreatZone

// ============================================================
//  PALETA DE COLORES DEL HUD
// ============================================================
private val HudBackground   = Color(0xFF0A0E1A)
private val HudSafe         = Color(0xFF00E5A0)
private val HudCaution      = Color(0xFF3A9EFF)
private val HudWarning      = Color(0xFFFFB800)
private val HudCritical     = Color(0xFFFF3B30)
private val HudText         = Color(0xFFE8EDF5)
private val HudTextMuted    = Color(0xFF6B7599)

/**
 * Pantalla HUD principal de Sentinel.
 * Diseñada para uso en posición landscape en el retrovisor Android.
 * La interfaz es mínima y de alto contraste para no distraer al conductor.
 */
@Composable
fun SentinelHudScreen(
    uiState: MainViewModel.UiState,
    modifier: Modifier = Modifier
) {
    val alertColor by animateColorAsState(
        targetValue = when (uiState.threatLevel) {
            ThreatLevel.CRITICAL -> HudCritical
            ThreatLevel.WARNING  -> HudWarning
            ThreatLevel.CAUTION  -> HudCaution
            ThreatLevel.NONE     -> HudSafe
        },
        animationSpec = tween(durationMillis = 150),
        label = "alertColor"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(HudBackground),
        contentAlignment = Alignment.Center
    ) {
        // Fondo de gradiente dinámico
        if (uiState.threatLevel != ThreatLevel.NONE) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(alertColor.copy(alpha = 0.12f), Color.Transparent),
                            radius = 800f
                        )
                    )
            )
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Panel izquierdo: Indicador de amenaza principal
            ThreatIndicatorPanel(
                uiState = uiState,
                alertColor = alertColor,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(24.dp))

            // Panel derecho: Radar direccional
            DirectionalRadarPanel(
                threatZone = if (uiState.threatLevel != ThreatLevel.NONE) uiState.threatZone else null,
                alertColor = alertColor,
                modifier = Modifier.size(180.dp)
            )
        }

        // Barra de estado inferior
        StatusBar(
            isRadarConnected = uiState.isRadarConnected,
            isHapticConnected = uiState.isHapticConnected,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp)
        )
    }
}

@Composable
private fun ThreatIndicatorPanel(
    uiState: MainViewModel.UiState,
    alertColor: Color,
    modifier: Modifier = Modifier
) {
    val pulseAnim = rememberInfiniteTransition(label = "pulse")
    val scale by pulseAnim.animateFloat(
        initialValue = 1f,
        targetValue = if (uiState.threatLevel == ThreatLevel.CRITICAL) 1.08f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Ícono de estado
        Box(
            modifier = Modifier
                .size(80.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(alertColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = when (uiState.threatLevel) {
                    ThreatLevel.CRITICAL -> "⚠️"
                    ThreatLevel.WARNING  -> "🔶"
                    ThreatLevel.CAUTION  -> "🔵"
                    ThreatLevel.NONE     -> "✅"
                },
                fontSize = 36.sp
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // TTC Display
        if (uiState.threatLevel != ThreatLevel.NONE && uiState.ttcSeconds < Float.MAX_VALUE) {
            Text(
                text = String.format("%.1f s", uiState.ttcSeconds),
                color = alertColor,
                fontSize = 52.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )
            Text(
                text = "TIEMPO AL IMPACTO",
                color = HudTextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 2.sp
            )
        } else {
            Text(
                text = "SENTINEL",
                color = HudSafe,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "MONITOREANDO",
                color = HudTextMuted,
                fontSize = 11.sp,
                letterSpacing = 3.sp
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Nivel de amenaza como badge
        if (uiState.threatLevel != ThreatLevel.NONE) {
            Surface(
                color = alertColor.copy(alpha = 0.2f),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = uiState.threatLevel.name,
                    color = alertColor,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
            }
        }
    }
}

/**
 * Panel de radar direccional simplificado.
 * Muestra un círculo con la dirección de la amenaza iluminada.
 */
@Composable
private fun DirectionalRadarPanel(
    threatZone: ThreatZone?,
    alertColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Fondo del radar
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(Color(0xFF111827))
        )

        // Indicadores direccionales
        val zones = listOf(
            Triple(ThreatZone.FRONT, Alignment.TopCenter, "▲"),
            Triple(ThreatZone.BACK, Alignment.BottomCenter, "▼"),
            Triple(ThreatZone.LEFT, Alignment.CenterStart, "◀"),
            Triple(ThreatZone.RIGHT, Alignment.CenterEnd, "▶"),
        )

        zones.forEach { (zone, align, icon) ->
            val isActive = zone == threatZone
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = align
            ) {
                Text(
                    text = icon,
                    color = if (isActive) alertColor else HudTextMuted.copy(alpha = 0.3f),
                    fontSize = if (isActive) 28.sp else 20.sp,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        // Texto central
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (threatZone != null) threatZone.name else "CLEAR",
                color = if (threatZone != null) alertColor else HudSafe,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun StatusBar(
    isRadarConnected: Boolean,
    isHapticConnected: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusDot(label = "RADAR", connected = isRadarConnected)
        StatusDot(label = "HÁPTICO", connected = isHapticConnected)
    }
}

@Composable
private fun StatusDot(label: String, connected: Boolean) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(if (connected) HudSafe else HudCritical)
        )
        Text(
            text = label,
            color = HudTextMuted,
            fontSize = 9.sp,
            letterSpacing = 1.5.sp
        )
    }
}

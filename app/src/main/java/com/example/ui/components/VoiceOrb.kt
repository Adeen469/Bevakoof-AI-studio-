package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.ui.theme.CrimsonCritical
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.EmeraldSafe
import com.example.ui.theme.IndigoNeon
import com.example.voice.AgentVoiceState
import com.example.voice.VoiceAcousticStats

@Composable
fun VoiceOrb(
    voiceState: AgentVoiceState,
    acousticStats: VoiceAcousticStats,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb_pulse")

    val idlePulse by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idle_pulse"
    )

    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing)
        ),
        label = "rotation"
    )

    // Base colors according to voice state
    val (primaryColor, glowColor) = when (voiceState) {
        AgentVoiceState.IDLE -> CyanNeon to IndigoNeon
        AgentVoiceState.LISTENING -> EmeraldSafe to CyanNeon
        AgentVoiceState.PROCESSING -> IndigoNeon to CyanNeon
        AgentVoiceState.SPEAKING -> CyanNeon to EmeraldSafe
        AgentVoiceState.EMERGENCY_STOPPED -> CrimsonCritical to Color(0xFF7F1D1D)
        AgentVoiceState.ERROR -> CrimsonCritical to Color(0xFF7F1D1D)
    }

    val dynamicScale = when (voiceState) {
        AgentVoiceState.LISTENING -> 1.0f + (acousticStats.rmsLevel * 0.45f)
        AgentVoiceState.SPEAKING -> 1.08f + (idlePulse - 1.0f) * 1.5f
        AgentVoiceState.IDLE -> idlePulse
        else -> 1.0f
    }

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(160.dp)
            .semantics {
                contentDescription = when (voiceState) {
                    AgentVoiceState.LISTENING -> "Bewakoof listening. Tap to pause."
                    AgentVoiceState.PROCESSING -> "Bewakoof thinking."
                    AgentVoiceState.SPEAKING -> "Bewakoof speaking. Tap to stop."
                    AgentVoiceState.EMERGENCY_STOPPED -> "Emergency stop engaged. Tap to reset."
                    else -> "Tap to speak to Bewakoof."
                }
            }
            .testTag("voice_orb")
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = false, radius = 80.dp),
                onClick = onClick
            )
    ) {
        Canvas(modifier = Modifier.size(150.dp)) {
            val center = Offset(size.width / 2, size.height / 2)
            val baseRadius = (size.minDimension / 2) * 0.7f

            // Outer reactive wave ring
            drawCircle(
                color = glowColor.copy(alpha = 0.25f),
                radius = baseRadius * dynamicScale * 1.25f,
                center = center
            )

            // Mid aura
            drawCircle(
                color = primaryColor.copy(alpha = 0.35f),
                radius = baseRadius * dynamicScale * 1.1f,
                center = center
            )

            // Inner core orb with radial gradient
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(primaryColor, glowColor, primaryColor.copy(alpha = 0.8f)),
                    center = center,
                    radius = baseRadius
                ),
                radius = baseRadius,
                center = center
            )
        }

        // Center Icon indicating current state
        val icon = when (voiceState) {
            AgentVoiceState.LISTENING -> Icons.Default.Mic
            AgentVoiceState.PROCESSING -> Icons.Default.Sync
            AgentVoiceState.SPEAKING -> Icons.Default.GraphicEq
            AgentVoiceState.EMERGENCY_STOPPED -> Icons.Default.StopCircle
            else -> Icons.Default.MicOff
        }

        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(42.dp)
        )
    }
}

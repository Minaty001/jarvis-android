package com.jarvis.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import com.jarvis.ui.theme.CyanGlow
import com.jarvis.ui.theme.DarkCardBg
import com.jarvis.ui.theme.White

@Composable
fun GlowingMicOrb(
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    onClick: () -> Unit = {}
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb")

    // Pulse only when active (listening)
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.93f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    // Outer ring alpha pulses when active
    val ringAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ringAlpha"
    )

    val scale = if (isActive) pulse else 1.0f
    val outerAlpha = if (isActive) ringAlpha else 0.3f
    val innerAlpha = if (isActive) 0.9f else 0.6f
    val bgAlpha = if (isActive) 0.15f else 0f

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(160.dp)
            .scale(scale)
            .clip(CircleShape)
            .clickable { onClick() }
    ) {
        // Outer glow ring
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(2.dp, CyanGlow.copy(alpha = outerAlpha), CircleShape)
                .background(CyanGlow.copy(alpha = bgAlpha), CircleShape)
        )
        // Middle ring
        Box(
            modifier = Modifier
                .size(130.dp)
                .border(3.dp, CyanGlow.copy(alpha = innerAlpha), CircleShape)
        )
        // Core button
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(if (isActive) CyanGlow.copy(alpha = 0.2f) else DarkCardBg)
                .border(2.dp, CyanGlow, CircleShape)
        ) {
            Icon(
                imageVector = if (isActive) Icons.Default.Mic else Icons.Default.MicOff,
                contentDescription = if (isActive) "Listening" else "Tap to speak",
                tint = if (isActive) CyanGlow else White,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}

package com.jarvis.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
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
    isActive: Boolean = true,
    onClick: () -> Unit = {}
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val animatedScale = if (isActive) scale else 1.0f

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(160.dp)
            .scale(animatedScale)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(2.dp, CyanGlow.copy(alpha = 0.5f), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(130.dp)
                .border(4.dp, CyanGlow.copy(alpha = 0.8f), CircleShape)
        )
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(DarkCardBg)
                .border(2.dp, CyanGlow, CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "Microphone",
                tint = White,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}

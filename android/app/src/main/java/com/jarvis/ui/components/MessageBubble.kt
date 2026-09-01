package com.jarvis.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.ui.theme.CyanGlow
import com.jarvis.ui.theme.DarkCardBg
import com.jarvis.ui.theme.TextGray
import com.jarvis.ui.theme.White

@Composable
fun MessageBubble(
    message: String,
    isUser: Boolean = false,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isUser) CyanGlow.copy(alpha = 0.5f) else CyanGlow
    val bgColor = DarkCardBg

    Box(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .background(bgColor, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Text(
            text = message,
            color = if (isUser) TextGray else White,
            fontSize = 15.sp,
            lineHeight = 22.sp
        )
    }
}

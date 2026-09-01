package com.jarvis.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import com.jarvis.ui.components.ActionCard
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.ui.theme.*

@Composable
fun MapScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(30.dp))
        Text(text = "JARVIS", color = CyanGlow, fontSize = 40.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, letterSpacing = 4.sp)
        Text(text = "Navigation", color = TextGray, fontSize = 14.sp, modifier = Modifier.padding(bottom = 40.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .border(1.dp, CyanGlow, RoundedCornerShape(12.dp))
                .background(DarkCardBg, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(imageVector = Icons.Default.Map, contentDescription = null, tint = CyanGlow, modifier = Modifier.size(64.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "Google Maps Integration", color = TextGray, fontSize = 14.sp)
                Text(text = "Say \"Navigate to...\" to start", color = CyanGlow.copy(alpha = 0.7f), fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ActionCard(icon = Icons.Default.Home, title = "Home", modifier = Modifier.weight(1f))
            ActionCard(icon = Icons.Default.Work, title = "Work", modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

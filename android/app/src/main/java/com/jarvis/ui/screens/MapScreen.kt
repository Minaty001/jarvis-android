package com.jarvis.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.ui.components.ActionCard
import com.jarvis.ui.theme.*

@Composable
fun MapScreen() {
    val context = LocalContext.current

    fun openMaps(query: String) {
        val uri = Uri.parse("geo:0,0?q=${Uri.encode(query)}")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.google.android.apps.maps")
        }
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        } else {
            // Fallback to browser
            val webUri = Uri.parse("https://maps.google.com/?q=${Uri.encode(query)}")
            context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(30.dp))
        Text(
            text = "JARVIS",
            color = CyanGlow,
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 4.sp
        )
        Text(
            text = "Navigation",
            color = TextGray,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .border(1.dp, CyanGlow.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                .background(DarkCardBg, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Map,
                    contentDescription = null,
                    tint = CyanGlow,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "Google Maps Integration", color = TextGray, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Say \"Navigate to...\" to start",
                    color = CyanGlow.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ActionCard(
                icon = Icons.Default.Home,
                title = "Home",
                modifier = Modifier.weight(1f),
                onClick = { openMaps("home") }
            )
            ActionCard(
                icon = Icons.Default.Work,
                title = "Work",
                modifier = Modifier.weight(1f),
                onClick = { openMaps("work") }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

package com.jarvis.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.backend.ApiClient
import com.jarvis.backend.SkillResult
import com.jarvis.config.Config
import com.jarvis.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun SkillScreen() {
    val scope = rememberCoroutineScope()
    val apiClient = remember { ApiClient() }
    var skills by remember { mutableStateOf(listOf<SkillResult>()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        isLoading = true
        skills = apiClient.listSkills(Config.DEVICE_ID)
        isLoading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(30.dp))
        Text(text = "JARVIS", color = CyanGlow, fontSize = 40.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, letterSpacing = 4.sp)
        Text(text = "Learned Skills", color = TextGray, fontSize = 14.sp, modifier = Modifier.padding(bottom = 16.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CyanGlow)
            }
        } else if (skills.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.School,
                        contentDescription = null,
                        tint = CyanGlow.copy(alpha = 0.4f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("No skills learned yet", color = TextGray, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Say \"learn to...\" to create a new skill",
                        color = TextGray,
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(skills) { skill ->
                    SkillItem(skill = skill, onDelete = { id ->
                        scope.launch {
                            apiClient.deleteSkill(id)
                            skills = skills.filter { it.id != id }
                        }
                    })
                }
            }
        }
    }
}

@Composable
private fun SkillItem(skill: SkillResult, onDelete: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CyanGlow.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .background(DarkCardBg, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.School,
            contentDescription = null,
            tint = CyanGlow,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = skill.name,
                color = androidx.compose.ui.graphics.Color.White,
                fontSize = 15.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
            )
            Text(
                text = "Trigger: ${skill.triggerPattern}",
                color = TextGray,
                fontSize = 12.sp
            )
            Text(
                text = "Used ${skill.usageCount} times",
                color = TextGray,
                fontSize = 11.sp
            )
        }
        IconButton(
            onClick = { onDelete(skill.id) },
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete",
                tint = TextGray,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

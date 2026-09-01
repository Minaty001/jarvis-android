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
import com.jarvis.backend.MemoryResult
import com.jarvis.config.Config
import com.jarvis.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun MemoryScreen() {
    val scope = rememberCoroutineScope()
    val apiClient = remember { ApiClient() }
    var memories by remember { mutableStateOf(listOf<MemoryResult>()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        isLoading = true
        memories = apiClient.getRecentMemories(Config.DEVICE_ID, 20)
        isLoading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(30.dp))
        Text(text = "JARVIS", color = CyanGlow, fontSize = 40.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, letterSpacing = 4.sp)
        Text(text = "Memory", color = TextGray, fontSize = 14.sp, modifier = Modifier.padding(bottom = 16.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = {
                searchQuery = it
                if (it.isNotBlank()) {
                    scope.launch {
                        memories = apiClient.searchMemory(Config.DEVICE_ID, it)
                    }
                } else {
                    scope.launch {
                        memories = apiClient.getRecentMemories(Config.DEVICE_ID, 20)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search memories...", color = TextGray) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = CyanGlow) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CyanGlow,
                unfocusedBorderColor = CyanGlow.copy(alpha = 0.3f),
                focusedTextColor = androidx.compose.ui.graphics.Color.White,
                unfocusedTextColor = androidx.compose.ui.graphics.Color.White
            ),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CyanGlow)
            }
        } else if (memories.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No memories yet", color = TextGray, fontSize = 16.sp)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(memories) { memory ->
                    MemoryItem(memory = memory, onDelete = { id ->
                        scope.launch {
                            apiClient.deleteMemory(id)
                            memories = memories.filter { it.id != id }
                        }
                    })
                }
            }
        }
    }
}

@Composable
private fun MemoryItem(memory: MemoryResult, onDelete: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CyanGlow.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .background(DarkCardBg, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = Icons.Default.Memory,
            contentDescription = null,
            tint = CyanGlow.copy(alpha = 0.6f),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = memory.content.take(120),
                color = androidx.compose.ui.graphics.Color.White,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }
        IconButton(
            onClick = { onDelete(memory.id) },
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

package com.jarvis.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunicationScreen(
    messages: List<Pair<String, Boolean>> = emptyList(),
    onSendMessage: (String) -> Unit = {}
) {
    var inputText by remember { mutableStateOf("") }

    val demoMessages = if (messages.isEmpty()) listOf(
        "Good morning! How can I help you today?" to false,
        "Open WhatsApp and message John" to true,
        "Opening WhatsApp now..." to false,
    ) else messages

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(30.dp))
        Text(text = "JARVIS", color = CyanGlow, fontSize = 40.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, letterSpacing = 4.sp)
        Text(text = "Communication", color = TextGray, fontSize = 14.sp, modifier = Modifier.padding(bottom = 16.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(demoMessages) { (msg, isUser) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                ) {
                    Box(
                        modifier = Modifier
                            .widthIn(max = 280.dp)
                            .border(1.dp, if (isUser) CyanGlow.copy(alpha = 0.5f) else CyanGlow, RoundedCornerShape(12.dp))
                            .background(DarkCardBg, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Text(text = msg, color = if (isUser) TextGray else androidx.compose.ui.graphics.Color.White, fontSize = 14.sp, lineHeight = 20.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type a command...", color = TextGray) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyanGlow,
                    unfocusedBorderColor = CyanGlow.copy(alpha = 0.5f),
                    cursorColor = CyanGlow,
                    focusedTextColor = androidx.compose.ui.graphics.Color.White,
                    unfocusedTextColor = androidx.compose.ui.graphics.Color.White,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (inputText.isNotBlank()) {
                        onSendMessage(inputText)
                        inputText = ""
                    }
                }),
                singleLine = true,
            )
            IconButton(onClick = {
                if (inputText.isNotBlank()) {
                    onSendMessage(inputText)
                    inputText = ""
                }
            }) {
                Icon(imageVector = Icons.Default.Send, contentDescription = "Send", tint = CyanGlow)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

package com.mnivesh.smsforwarder.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    // keep local state synced with sharedprefs so UI updates immediately
    var whitelist by remember {
        val initialSet = prefs.getStringSet("whitelist_senders", emptySet()) ?: emptySet()
        mutableStateOf(initialSet.toList())
    }

    var showAddDialog by remember { mutableStateOf(false) }

    val bgColor = Color(0xFF0F172A)
    val cardColor = Color(0xFF1E293B)
    val primaryColor = Color(0xFF3B82F6)

    Scaffold(
        containerColor = bgColor,
        topBar = {
            TopAppBar(
                title = { Text("Whitelist Management", color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bgColor),
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.White)
                    }
                }
            )
        }
    ) { padding ->
        if (whitelist.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("No senders whitelisted yet.", color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(whitelist) { sender ->
                    WhitelistItem(
                        sender = sender,
                        cardColor = cardColor,
                        onDelete = {
                            val updatedSet = whitelist.toMutableSet().apply { remove(sender) }
                            prefs.edit().putStringSet("whitelist_senders", updatedSet).apply()
                            whitelist = updatedSet.toList()
                        }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        var newSender by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Sender to Whitelist") },
            text = {
                OutlinedTextField(
                    value = newSender,
                    onValueChange = { newSender = it },
                    label = { Text("Sender ID (e.g., HDFC-T)") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newSender.isNotBlank()) {
                        val trimmed = newSender.trim()
                        val updatedSet = whitelist.toMutableSet().apply { add(trimmed) }
                        // string set requires a new object to register changes in prefs
                        prefs.edit().putStringSet("whitelist_senders", updatedSet).apply()
                        whitelist = updatedSet.toList()
                    }
                    showAddDialog = false
                }) {
                    Text("Save", color = primaryColor)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        )
    }
}

@Composable
fun WhitelistItem(sender: String, cardColor: Color, onDelete: () -> Unit) {
    Surface(
        color = cardColor,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = sender,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red.copy(alpha = 0.8f))
            }
        }
    }
}
package com.autosend.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.autosend.data.MessageWithAttachments
import com.autosend.data.SendStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleListScreen(
    vm: ScheduleViewModel,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    onOpenOnboarding: () -> Unit,
) {
    val messages by vm.messages.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AutoSend") },
                actions = {
                    IconButton(onClick = onOpenOnboarding) {
                        Icon(Icons.Filled.Settings, contentDescription = "Permisos y ajustes")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(Icons.Filled.Add, contentDescription = "Programar mensaje")
            }
        }
    ) { padding ->
        if (messages.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Aún no hay mensajes programados.", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Toca + para crear uno. Revisa primero los permisos en el ícono de ajustes.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(messages, key = { it.message.id }) { item ->
                    MessageCard(item, onClick = { onEdit(item.message.id) }, onDelete = { vm.delete(item.message.id) })
                }
            }
        }
    }
}

@Composable
private fun MessageCard(
    item: MessageWithAttachments,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val m = item.message
    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = m.contactName.ifBlank { m.phoneE164.ifBlank { "(sin destinatario)" } },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${m.targetApp.label} · ${formatTime(m.scheduleAtMillis)}",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (m.text.isNotBlank()) {
                    Text(
                        text = m.text,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val extras = buildList {
                    if (item.attachments.isNotEmpty()) add("${item.attachments.size} adjunto(s)")
                    add(statusLabel(m.status))
                    if (!m.autoSend) add("semi-automático")
                }
                Text(extras.joinToString(" · "), style = MaterialTheme.typography.labelSmall)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Eliminar")
            }
        }
    }
}

private val formatter =
    DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale("es")).withZone(ZoneId.systemDefault())

private fun formatTime(millis: Long): String =
    if (millis <= 0L) "sin fecha" else formatter.format(Instant.ofEpochMilli(millis))

private fun statusLabel(status: SendStatus): String = when (status) {
    SendStatus.PENDING -> "programado"
    SendStatus.SENDING -> "enviando…"
    SendStatus.SENT -> "enviado ✓"
    SendStatus.FAILED -> "falló ✗"
    SendStatus.CANCELED -> "cancelado"
}

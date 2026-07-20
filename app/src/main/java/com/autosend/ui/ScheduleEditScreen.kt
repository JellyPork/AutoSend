package com.autosend.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.autosend.data.ScheduledMessage
import com.autosend.data.SendStatus
import com.autosend.data.TargetApp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleEditScreen(
    vm: ScheduleViewModel,
    messageId: Long?,
    onDone: () -> Unit,
) {
    val context = LocalContext.current

    var loaded by remember { mutableStateOf(messageId == null) }
    var existingId by remember { mutableStateOf(0L) }

    var targetApp by remember { mutableStateOf(TargetApp.WHATSAPP) }
    var contactName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    var autoSend by remember { mutableStateOf(true) }
    var scheduleMillis by remember { mutableStateOf(defaultTime()) }
    val newAttachments = remember { mutableStateListOf<Uri>() }
    var existingAttachmentNames by remember { mutableStateOf<List<String>>(emptyList()) }

    // Load existing message once when editing.
    LaunchedEffect(messageId) {
        if (messageId != null) {
            vm.load(messageId) { data ->
                if (data != null) {
                    existingId = data.message.id
                    targetApp = data.message.targetApp
                    contactName = data.message.contactName
                    phone = data.message.phoneE164
                    text = data.message.text
                    autoSend = data.message.autoSend
                    scheduleMillis = data.message.scheduleAtMillis.takeIf { it > 0 } ?: defaultTime()
                    existingAttachmentNames = data.attachments.map { it.displayName }
                }
                loaded = true
            }
        }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> newAttachments.addAll(uris) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (messageId == null) "Nuevo mensaje" else "Editar mensaje") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppSelector(targetApp) { targetApp = it }

            OutlinedTextField(
                value = contactName,
                onValueChange = { contactName = it },
                label = { Text("Nombre del contacto (como aparece en la app)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it.filter { c -> c.isDigit() } },
                label = { Text("Teléfono con código de país, sin +  (ej. 5215512345678)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Mensaje") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            DateTimeRow(scheduleMillis) { pickDateTime(context, scheduleMillis) { scheduleMillis = it } }

            AttachmentsSection(
                existingNames = existingAttachmentNames,
                newUris = newAttachments,
                onAdd = { picker.launch(arrayOf("*/*")) },
                onRemoveNew = { newAttachments.remove(it) },
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = autoSend, onCheckedChange = { autoSend = it })
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text("Envío automático", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (autoSend) "La app pulsará Enviar por ti."
                        else "Solo abrirá el chat; tú tocas Enviar.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Button(
                onClick = {
                    val error = validate(contactName, phone, targetApp, scheduleMillis)
                    if (error != null) {
                        Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                        return@Button
                    }
                    val message = ScheduledMessage(
                        id = existingId,
                        targetApp = targetApp,
                        contactName = contactName.trim(),
                        phoneE164 = phone.trim(),
                        text = text,
                        scheduleAtMillis = scheduleMillis,
                        status = SendStatus.PENDING,
                        autoSend = autoSend,
                    )
                    vm.save(message, newAttachments.toList()) {
                        Toast.makeText(context, "Mensaje programado", Toast.LENGTH_SHORT).show()
                        onDone()
                    }
                },
                enabled = loaded,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Guardar y programar")
            }
        }
    }
}

@Composable
private fun AppSelector(selected: TargetApp, onSelect: (TargetApp) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text("App destino", style = MaterialTheme.typography.labelMedium)
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(selected.label, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                // Messenger is intentionally NOT selectable: its "Send to…" sheet has a Send button
                // per contact, which the automation can't drive safely yet.
                TargetApp.entries.filter { it != TargetApp.MESSENGER }.forEach { app ->
                    DropdownMenuItem(
                        text = { Text(app.label) },
                        onClick = { onSelect(app); expanded = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun DateTimeRow(millis: Long, onPick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = onPick) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Fecha y hora de envío", style = MaterialTheme.typography.labelMedium)
            Text(prettyDateTime(millis), style = MaterialTheme.typography.titleMedium)
            Text("Toca para cambiar", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun AttachmentsSection(
    existingNames: List<String>,
    newUris: List<Uri>,
    onAdd: () -> Unit,
    onRemoveNew: (Uri) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Adjuntos", style = MaterialTheme.typography.labelLarge)
        existingNames.forEach { name ->
            Text("• $name (ya guardado)", style = MaterialTheme.typography.bodySmall)
        }
        newUris.forEach { uri ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "• ${uri.lastPathSegment ?: uri.toString()}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = { onRemoveNew(uri) }) { Text("Quitar") }
            }
        }
        OutlinedButton(onClick = onAdd) { Text("Agregar imagen o archivo") }
    }
}

// --- helpers ---

private fun defaultTime(): Long =
    Calendar.getInstance().apply { add(Calendar.MINUTE, 10) }.timeInMillis

private fun validate(contactName: String, phone: String, target: TargetApp, millis: Long): String? {
    if (target != TargetApp.MESSENGER && phone.isBlank() && contactName.isBlank())
        return "Escribe el teléfono (para WhatsApp) o al menos el nombre del contacto."
    if (target == TargetApp.MESSENGER && contactName.isBlank())
        return "Para Messenger necesitas el nombre del contacto."
    if (millis <= System.currentTimeMillis())
        return "La fecha y hora deben estar en el futuro."
    return null
}

private fun pickDateTime(
    context: android.content.Context,
    current: Long,
    onResult: (Long) -> Unit,
) {
    val cal = Calendar.getInstance().apply { timeInMillis = current }
    DatePickerDialog(
        context,
        { _, year, month, day ->
            TimePickerDialog(
                context,
                { _, hour, minute ->
                    val picked = Calendar.getInstance().apply {
                        set(Calendar.YEAR, year)
                        set(Calendar.MONTH, month)
                        set(Calendar.DAY_OF_MONTH, day)
                        set(Calendar.HOUR_OF_DAY, hour)
                        set(Calendar.MINUTE, minute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    onResult(picked.timeInMillis)
                },
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE),
                true,
            ).show()
        },
        cal.get(Calendar.YEAR),
        cal.get(Calendar.MONTH),
        cal.get(Calendar.DAY_OF_MONTH),
    ).show()
}

private val prettyFormatter =
    DateTimeFormatter.ofPattern("EEEE d 'de' MMMM, HH:mm", Locale("es")).withZone(ZoneId.systemDefault())

private fun prettyDateTime(millis: Long): String =
    prettyFormatter.format(Instant.ofEpochMilli(millis))

package com.autosend.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.autosend.util.Permissions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    // Re-check statuses whenever we come back from a settings screen.
    var refresh by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Recomputed on every ON_RESUME (and after the notification prompt) so the rows stay accurate.
    val a11yGranted = remember(refresh) { Permissions.isAccessibilityEnabled(context) }
    val exactGranted = remember(refresh) { Permissions.canScheduleExactAlarms(context) }
    val notifGranted = remember(refresh) { Permissions.hasNotificationPermission(context) }
    val batteryGranted = remember(refresh) { Permissions.isIgnoringBatteryOptimizations(context) }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refresh++ }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Permisos") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
            Text(
                "Para enviar mensajes por ti, AutoSend necesita estos permisos especiales. " +
                    "Actívalos una sola vez.",
                style = MaterialTheme.typography.bodyMedium,
            )

            PermissionRow(
                title = "Servicio de accesibilidad",
                description = "Permite pulsar el botón Enviar por ti. Es el permiso clave.",
                granted = a11yGranted,
                onFix = { context.startActivitySafe(Permissions.accessibilitySettings()) },
            )

            PermissionRow(
                title = "Alarmas exactas",
                description = "Necesario para disparar el envío justo a la hora programada.",
                granted = exactGranted,
                onFix = { context.startActivitySafe(Permissions.exactAlarmSettings(context)) },
            )

            PermissionRow(
                title = "Notificaciones",
                description = "Muestra el estado del envío y despierta la pantalla al enviar.",
                granted = notifGranted,
                onFix = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        context.startActivitySafe(Permissions.appNotificationSettings(context))
                    }
                },
            )

            PermissionRow(
                title = "Sin optimización de batería",
                description = "Evita que el sistema retrase o cancele los envíos en segundo plano.",
                granted = batteryGranted,
                onFix = { context.startActivitySafe(Permissions.requestIgnoreBatteryOptimizations(context)) },
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Sobre el desbloqueo automático", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Android no permite que ninguna app quite un bloqueo seguro (PIN, patrón o huella). " +
                            "Para que el envío sea 100% automático con la pantalla bloqueada, usa un bloqueo " +
                            "\"Deslizar/Ninguno\" o activa Smart Lock (lugar/dispositivo de confianza). " +
                            "Con PIN/huella, AutoSend encenderá la pantalla y dejará el chat listo, pero tú " +
                            "deberás desbloquear para que se envíe.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    description: String,
    granted: Boolean,
    onFix: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = (if (granted) "✓ " else "✗ ") + title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
                Text(description, style = MaterialTheme.typography.bodySmall)
            }
            if (!granted) {
                Button(onClick = onFix) { Text("Activar") }
            }
        }
    }
}

private fun Context.startActivitySafe(intent: Intent) {
    runCatching {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

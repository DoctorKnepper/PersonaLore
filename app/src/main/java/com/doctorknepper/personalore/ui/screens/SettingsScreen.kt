package com.doctorknepper.personalore.ui.screens

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.doctorknepper.personalore.data.Store
import com.doctorknepper.personalore.ui.AppCard
import com.doctorknepper.personalore.ui.BackBar
import com.doctorknepper.personalore.ui.Colorways
import com.doctorknepper.personalore.ui.LocalNav
import com.doctorknepper.personalore.ui.SectionLabel
import com.doctorknepper.personalore.ui.ThemeMode
import com.doctorknepper.personalore.util.Reminders
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val nav = LocalNav.current
    val context = LocalContext.current
    var pinDialog by remember { mutableStateOf(false) }
    var timeDialog by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf<android.net.Uri?>(null) }
    var busy by remember { mutableStateOf(false) }

    val canBiometric = remember {
        BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
    }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) { Store.setReminder(true); Reminders.schedule(context) }
        else Toast.makeText(context, "Notifications are off for PersonaLore, so reminders can't show.", Toast.LENGTH_LONG).show()
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            val r = Store.exportBackup(uri)
            Toast.makeText(context, r.fold({ "Backup saved with $it entries." }, { "Backup failed: ${it.message}" }), Toast.LENGTH_LONG).show()
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) confirmRestore = uri }

    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { BackBar("Settings", onBack = { nav.pop() }) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {

            SectionLabel("Appearance")
            AppCard {
                Text("Mode", style = MaterialTheme.typography.titleSmall)
                Row(Modifier.padding(vertical = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { m -> FilterChip(selected = Store.themeMode == m, onClick = { Store.chooseThemeMode(m) }, label = { Text(m.label) }) }
                }
                Text("Colorway", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                Row(Modifier.padding(vertical = 10.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Colorways.forEach { cw ->
                        val sel = Store.colorway == cw.id
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { Store.chooseColorway(cw.id) }) {
                            Box(
                                Modifier.size(44.dp).background(Color.Black, CircleShape)
                                    .border(if (sel) 3.dp else 1.dp, if (sel) cw.darkAccent else MaterialTheme.colorScheme.outline, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(Modifier.size(24.dp).background(cw.darkAccent, CircleShape), contentAlignment = Alignment.Center) {
                                    if (sel) Icon(Icons.Filled.Check, null, Modifier.size(16.dp), tint = Color.Black)
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(cw.name, style = MaterialTheme.typography.labelSmall,
                                color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Text("Every colorway works in both dark and light mode.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            SectionLabel("Privacy")
            AppCard {
                SettingSwitch("App lock", "Ask for a PIN when opening PersonaLore", Store.lockEnabled) { on ->
                    if (on) pinDialog = true else Store.disableLock()
                }
                if (Store.lockEnabled) {
                    SettingSwitch(
                        "Unlock with fingerprint",
                        if (canBiometric) "Use your fingerprint or face instead of typing the PIN" else "No fingerprint or face unlock is set up on this phone",
                        Store.biometricEnabled, enabled = canBiometric,
                    ) { Store.setBiometric(it) }
                    TextButton(onClick = { pinDialog = true }) { Text("Change PIN") }
                }
                Text("Your journal is stored only on this phone and is never uploaded anywhere.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            }

            SectionLabel("Daily reminder")
            AppCard {
                SettingSwitch("Remind me to write", "Shows today's prompt if you haven't written yet", Store.reminderEnabled) { on ->
                    if (on && Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else { Store.setReminder(on); Reminders.schedule(context) }
                }
                if (Store.reminderEnabled) {
                    val t = LocalTime.of(Store.reminderHour, Store.reminderMinute)
                    TextButton(onClick = { timeDialog = true }) { Text("Time: ${t.format(DateTimeFormatter.ofPattern("h:mm a"))} · change") }
                }
            }

            SectionLabel("Backup")
            AppCard {
                Text("Save everything (entries, photos, voice notes, prompt progress) to one backup file. Keep it somewhere safe, like your computer or cloud drive.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = !busy, onClick = { exportLauncher.launch("PersonaLore-backup-${LocalDate.now()}.zip") }) { Text("Export backup") }
                    OutlinedButton(enabled = !busy, onClick = { restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }) { Text("Restore") }
                }
            }

            SectionLabel("About")
            Text("PersonaLore 1.1 · your life, in your own words.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(48.dp))
        }
    }

    if (pinDialog) SetPinDialog(onDismiss = { pinDialog = false }) { Store.setPin(it); pinDialog = false }

    if (timeDialog) {
        val state = rememberTimePickerState(Store.reminderHour, Store.reminderMinute, is24Hour = false)
        AlertDialog(
            onDismissRequest = { timeDialog = false },
            title = { Text("Reminder time") },
            text = { TimePicker(state) },
            confirmButton = { TextButton(onClick = {
                Store.setReminder(true, state.hour, state.minute); Reminders.schedule(context); timeDialog = false
            }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { timeDialog = false }) { Text("Cancel") } },
        )
    }

    confirmRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirmRestore = null },
            title = { Text("Restore this backup?") },
            text = { Text("Everything currently in PersonaLore will be replaced by the backup's contents. Consider exporting a backup of what's here first.") },
            confirmButton = { TextButton(onClick = {
                confirmRestore = null; busy = true
                val r = Store.restoreBackup(uri)
                busy = false
                Toast.makeText(context, r.fold({ "Restored $it entries." }, { "Restore failed: ${it.message}" }), Toast.LENGTH_LONG).show()
            }) { Text("Replace everything") } },
            dismissButton = { TextButton(onClick = { confirmRestore = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SettingSwitch(title: String, sub: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun SetPinDialog(onDismiss: () -> Unit, onSet: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val valid = pin.length in 4..8 && pin == confirm
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set a PIN") },
        text = {
            Column {
                Text("4 to 8 digits. If you forget it, the journal can't be opened, so keep a backup.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(pin, { if (it.length <= 8 && it.all(Char::isDigit)) pin = it }, label = { Text("PIN") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(confirm, { if (it.length <= 8 && it.all(Char::isDigit)) confirm = it }, label = { Text("Confirm PIN") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    isError = confirm.isNotEmpty() && confirm != pin)
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = { onSet(pin) }) { Text("Set PIN") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

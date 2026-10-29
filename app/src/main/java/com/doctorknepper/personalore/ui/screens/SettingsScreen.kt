package com.doctorknepper.personalore.ui.screens

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.doctorknepper.personalore.data.Store
import com.doctorknepper.personalore.ui.AppCard
import com.doctorknepper.personalore.ui.AppLock
import com.doctorknepper.personalore.ui.BackBar
import com.doctorknepper.personalore.ui.Colorways
import com.doctorknepper.personalore.ui.LocalNav
import com.doctorknepper.personalore.ui.Route
import com.doctorknepper.personalore.ui.SectionLabel
import com.doctorknepper.personalore.ui.Snacks
import com.doctorknepper.personalore.ui.ThemeMode
import com.doctorknepper.personalore.util.Reminders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen() {
    val nav = LocalNav.current
    val context = LocalContext.current
    val activity = context as? AppCompatActivity
    val scope = rememberCoroutineScope()

    var pinDialog by remember { mutableStateOf<String?>(null) } // "enable" or "change"
    var recoveryKey by remember { mutableStateOf<String?>(null) }
    var confirmNewRecovery by remember { mutableStateOf(false) }
    var timeDialog by remember { mutableStateOf(false) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var snapshotToRestore by remember { mutableStateOf<Pair<LocalDate, File>?>(null) }
    var undoTick by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf<String?>(null) }

    val canBiometric = remember {
        BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun work(label: String, block: suspend () -> Unit) {
        busy = label
        scope.launch { try { block() } finally { busy = null } }
    }

    // Runs outside this screen's scope, because the snackbar's Undo can be tapped after leaving Settings.
    fun undoRestore() {
        kotlinx.coroutines.MainScope().launch {
            val r = withContext(Dispatchers.IO) { Store.undoLastRestore() }
            undoTick++
            Snacks.show(r.fold({ "Restore undone. $it entries." }, { "Couldn't undo: ${it.message}" }))
        }
    }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) { Store.setReminder(true); Reminders.schedule(context) }
        else Snacks.show("Notifications are off for PersonaLore, so reminders can't show.")
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) work("Saving backup…") {
            val r = withContext(Dispatchers.IO) { Store.exportBackup(uri) }
            Snacks.show(r.fold({ "Backup saved with $it entries." }, { "Backup failed: ${it.message}" }))
        }
    }
    val markdownLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) work("Exporting…") {
            val r = withContext(Dispatchers.IO) { Store.exportMarkdown(uri) }
            Snacks.show(r.fold({ "Exported $it entries as Markdown." }, { "Export failed: ${it.message}" }))
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) restoreUri = uri }

    fun enrollBiometric() {
        val activity = activity ?: return
        val cipher = runCatching { Store.biometricEnrollCipher() }.getOrElse { Snacks.show("Fingerprint unlock isn't available on this phone."); return }
        BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                result.cryptoObject?.cipher?.let { Store.finishBiometricEnroll(it) }
            }
        }).authenticate(
            BiometricPrompt.PromptInfo.Builder().setTitle("Turn on fingerprint unlock").setNegativeButtonText("Cancel")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG).build(),
            BiometricPrompt.CryptoObject(cipher),
        )
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { BackBar("Settings", onBack = { nav.pop() }) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            busy?.let { label ->
                Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 6.dp))
            }

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
                SettingSwitch("App lock", "Ask for a PIN to open PersonaLore", Store.lockEnabled) { on ->
                    if (on) pinDialog = "enable" else { Store.disableLock(); Snacks.show("App lock is off.") }
                }
                if (Store.lockEnabled) {
                    SettingSwitch(
                        "Unlock with fingerprint",
                        if (canBiometric) "Use your fingerprint instead of typing the PIN" else "Needs a fingerprint (or strong face unlock) set up on this phone",
                        Store.biometricEnabled, enabled = canBiometric,
                    ) { if (it) enrollBiometric() else Store.disableBiometric() }
                    Text("Lock again", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                    FlowRow(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0 to "As soon as I leave", 60 to "After 1 minute", 300 to "After 5 minutes").forEach { (sec, label) ->
                            FilterChip(selected = Store.relockAfterSec == sec, onClick = { Store.setRelockAfter(sec) }, label = { Text(label) })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { pinDialog = "change" }) { Text("Change PIN") }
                        TextButton(onClick = { confirmNewRecovery = true }) { Text("New recovery key") }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)
                SettingSwitch("Block screenshots", "Also hides your journal in the recent apps screen", Store.blockScreenshots) { Store.chooseBlockScreenshots(it) }
                Text("Your journal, photos and voice notes are encrypted on this phone and never uploaded anywhere.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            }

            SectionLabel("Daily reminder")
            AppCard {
                SettingSwitch("Remind me to write", "Skipped on days you've already written", Store.reminderEnabled) { on ->
                    if (on && Build.VERSION.SDK_INT >= 33) { AppLock.leavingForPicker(); notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }
                    else { Store.setReminder(on); Reminders.schedule(context) }
                }
                if (Store.reminderEnabled) {
                    val t = LocalTime.of(Store.reminderHour, Store.reminderMinute)
                    TextButton(onClick = { timeDialog = true }) { Text("Time: ${t.format(DateTimeFormatter.ofPattern("h:mm a"))} · change") }
                    if (Store.lockEnabled) Text("With the lock on, the reminder doesn't show your prompt, so nothing from your journal appears on the lock screen.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            SectionLabel("Backup and export")
            AppCard {
                Text("Backup file", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("Everything (entries, photos, voice notes, prompt progress) in one file you can restore later or on a new phone. The file isn't encrypted, so keep it somewhere private.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = busy == null, onClick = { AppLock.leavingForPicker(); exportLauncher.launch("PersonaLore-backup-${LocalDate.now()}.zip") }) { Text("Export backup") }
                    OutlinedButton(enabled = busy == null, onClick = { AppLock.leavingForPicker(); restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }) { Text("Restore") }
                }
                HorizontalDivider(Modifier.padding(vertical = 14.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Text("Readable copy", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("Your journal as Markdown text files with the photos and voice notes alongside. Opens in any text editor, so your writing outlives the app.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(enabled = busy == null, modifier = Modifier.padding(top = 10.dp),
                    onClick = { AppLock.leavingForPicker(); markdownLauncher.launch("PersonaLore-journal-${LocalDate.now()}.zip") }) { Text("Export as Markdown") }
            }

            SectionLabel("Automatic snapshots")
            AppCard {
                Text("PersonaLore keeps a daily copy of your journal from each of the last ${Store.SNAPSHOTS_KEPT} days you used it. If something goes wrong, roll back to one.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val undoAt = remember(busy, undoTick) { Store.undoRestoreTakenAt() }
                if (undoAt != null) Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Before your last restore", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        Text(java.time.Instant.ofEpochMilli(undoAt).atZone(java.time.ZoneId.systemDefault())
                            .format(DateTimeFormatter.ofPattern("MMM d, h:mm a")), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(enabled = busy == null, onClick = { undoRestore() }) { Text("Undo restore") }
                }
                val snaps = remember(busy) { Store.snapshots() }
                if (snaps.isEmpty()) Text("No snapshots yet.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                snaps.forEach { snap ->
                    val count = remember(snap.second.path) { Store.snapshotEntryCount(snap.second) }
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(snap.first.format(DateTimeFormatter.ofPattern("EEE, MMM d")), style = MaterialTheme.typography.bodyMedium)
                            Text(if (count == null) "Can't be opened" else "$count entries", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(enabled = count != null && busy == null, onClick = { snapshotToRestore = snap }) { Text("Restore") }
                    }
                }
            }

            SectionLabel("Trash")
            AppCard(onClick = { nav.push(Route.Trash) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Trash", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(Store.trash.size.let { if (it == 0) "Empty" else "$it ${if (it == 1) "entry" else "entries"} · kept for ${Store.TRASH_DAYS} days" },
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            SectionLabel("About")
            Text("PersonaLore 1.4 · your life, in your own words.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(48.dp))
        }
    }

    pinDialog?.let { mode ->
        SetPinDialog(title = if (mode == "enable") "Set a PIN" else "Change PIN", firstTime = mode == "enable", onDismiss = { pinDialog = null }) { pin ->
            pinDialog = null
            work("Securing your journal…") {
                if (mode == "enable") recoveryKey = withContext(Dispatchers.Default) { Store.enableLock(pin) }
                else { withContext(Dispatchers.Default) { Store.changePin(pin) }; Snacks.show("PIN changed.") }
            }
        }
    }
    recoveryKey?.let { rk -> RecoveryKeyDialog(rk) { recoveryKey = null; Snacks.show("App lock is on.") } }

    if (confirmNewRecovery) AlertDialog(
        onDismissRequest = { confirmNewRecovery = false },
        title = { Text("Make a new recovery key?") },
        text = { Text("Your old recovery key will stop working. Do this if you've lost it or think someone else has seen it.") },
        confirmButton = { TextButton(onClick = {
            confirmNewRecovery = false
            work("Making a new key…") { recoveryKey = withContext(Dispatchers.Default) { Store.regenerateRecoveryKey() } }
        }) { Text("Make new key") } },
        dismissButton = { TextButton(onClick = { confirmNewRecovery = false }) { Text("Cancel") } },
    )

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

    restoreUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { restoreUri = null },
            title = { Text("Restore this backup") },
            text = {
                Column {
                    Text("Merge adds the backup's entries that aren't already here and keeps everything you have now. Nothing is overwritten.",
                        style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(10.dp))
                    Text("Replace puts the backup in place of what's here. You can undo it afterwards from this screen.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = {
                        restoreUri = null
                        work("Restoring…") {
                            val r = withContext(Dispatchers.IO) { Store.restoreBackup(uri, merge = false) }
                            r.fold({ Snacks.show("Replaced with ${it.added} entries from the backup.", "Undo") { undoRestore() } }, { Snacks.show("Restore failed: ${it.message}") })
                        }
                    }) { Text("Replace everything", color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = { Button(onClick = {
                restoreUri = null
                work("Restoring…") {
                    val r = withContext(Dispatchers.IO) { Store.restoreBackup(uri, merge = true) }
                    Snacks.show(r.fold({ "Added ${it.added} entries${if (it.skipped > 0) ", skipped ${it.skipped} already here" else ""}." }, { "Restore failed: ${it.message}" }))
                }
            }) { Text("Merge") } },
            dismissButton = { TextButton(onClick = { restoreUri = null }) { Text("Cancel") } },
        )
    }

    snapshotToRestore?.let { (date, dir) ->
        AlertDialog(
            onDismissRequest = { snapshotToRestore = null },
            title = { Text("Roll back to ${date.format(DateTimeFormatter.ofPattern("MMM d"))}?") },
            text = { Text("Your entries and prompt progress will go back to how they were that day. You can undo this afterwards from this screen.") },
            confirmButton = { TextButton(onClick = {
                snapshotToRestore = null
                work("Rolling back…") {
                    val r = withContext(Dispatchers.IO) { Store.restoreSnapshot(dir) }
                    r.fold({ Snacks.show("Rolled back. $it entries.", "Undo") { undoRestore() } }, { Snacks.show("Couldn't roll back: ${it.message}") })
                }
            }) { Text("Roll back") } },
            dismissButton = { TextButton(onClick = { snapshotToRestore = null }) { Text("Cancel") } },
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

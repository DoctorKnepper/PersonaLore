package com.doctorknepper.personalore.ui.screens

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties

@Composable
fun SetPinDialog(title: String = "Set a PIN", firstTime: Boolean, onDismiss: (() -> Unit)?, onSet: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val valid = pin.length in 4..8 && pin == confirm
    AlertDialog(
        onDismissRequest = { onDismiss?.invoke() },
        properties = DialogProperties(dismissOnClickOutside = onDismiss != null, dismissOnBackPress = onDismiss != null),
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("4 to 8 digits.", style = MaterialTheme.typography.bodySmall)
                if (firstTime) Text(
                    "Next you'll get a recovery key, the only way back in if you forget this PIN. If you lose both, your journal can't be opened, so export a backup too.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp),
                )
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
        dismissButton = onDismiss?.let { d -> { TextButton(onClick = d) { Text("Cancel") } } },
    )
}

/** Shows a recovery key once. The user must confirm they've saved it before closing. */
@Composable
fun RecoveryKeyDialog(recoveryKey: String, onDone: () -> Unit) {
    val context = LocalContext.current
    var saved by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnClickOutside = false, dismissOnBackPress = false),
        title = { Text("Your recovery key") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("If you ever forget your PIN, this key is the only way to open your journal. Write it down and keep it somewhere safe, away from your phone. It won't be shown again.",
                    style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(14.dp))
                Text(
                    recoveryKey, fontFamily = FontFamily.Monospace, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer).padding(14.dp),
                )
                TextButton(onClick = {
                    val cm = context.getSystemService(ClipboardManager::class.java)
                    val clip = ClipData.newPlainText("PersonaLore recovery key", recoveryKey)
                    // Mark as sensitive so Android 13+ doesn't show it in the clipboard preview.
                    if (Build.VERSION.SDK_INT >= 33) clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
                    cm.setPrimaryClip(clip)
                    copied = true
                }) { Text(if (copied) "Copied" else "Copy") }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(saved, { saved = it })
                    Text("I've saved my recovery key", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = { TextButton(enabled = saved, onClick = onDone) { Text("Done") } },
    )
}

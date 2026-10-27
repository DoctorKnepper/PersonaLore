package com.doctorknepper.personalore.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.doctorknepper.personalore.data.AudioClip
import com.doctorknepper.personalore.data.Store
import com.doctorknepper.personalore.data.moodOf
import com.doctorknepper.personalore.ui.BackBar
import com.doctorknepper.personalore.ui.DayFmt
import com.doctorknepper.personalore.ui.LocalNav
import com.doctorknepper.personalore.ui.Route
import com.doctorknepper.personalore.ui.SectionLabel
import com.doctorknepper.personalore.ui.TagPill
import com.doctorknepper.personalore.util.VoicePlayer
import com.doctorknepper.personalore.util.formatDuration
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EntryViewScreen(entryId: String) {
    val nav = LocalNav.current
    val e = Store.entries.firstOrNull { it.id == entryId }
    var confirmDelete by remember { mutableStateOf(false) }
    var fullPhoto by remember { mutableStateOf<String?>(null) }

    if (e == null) {
        Scaffold(topBar = { BackBar("Entry", onBack = { nav.pop() }) }) { pad ->
            Text("This entry was deleted.", Modifier.padding(pad).padding(16.dp))
        }
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            BackBar(e.date.format(DayFmt), onBack = { nav.pop() }) {
                IconButton(onClick = { nav.push(Route.Editor(entryId = e.id)) }) { Icon(Icons.Outlined.Edit, "Edit") }
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, "Delete") }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            if (e.promptText != null) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.primaryContainer).padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(6.dp))
                        Text("Prompt", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    Text(e.promptText, style = MaterialTheme.typography.titleMedium, fontStyle = FontStyle.Italic)
                }
                Spacer(Modifier.height(12.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (e.title.isNotBlank()) Text(e.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                else Spacer(Modifier.weight(1f))
                moodOf(e.mood)?.let { m ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(m.emoji, style = MaterialTheme.typography.headlineMedium)
                        Text(m.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            SelectionContainer { Text(e.body, style = MaterialTheme.typography.bodyLarge) }

            if (e.photos.isNotEmpty()) {
                SectionLabel("Photos")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(e.photos) { p ->
                        AsyncImage(
                            model = File(Store.mediaDir, p), contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.size(140.dp).clip(RoundedCornerShape(14.dp)).clickable { fullPhoto = p },
                        )
                    }
                }
            }
            if (e.audio.isNotEmpty()) {
                SectionLabel("Voice notes")
                e.audio.forEachIndexed { i, clip -> VoiceClipRow(clip, "Voice note ${i + 1}") }
            }
            if (e.tags.isNotEmpty()) {
                SectionLabel("Tags")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    e.tags.forEach { t -> TagPill(t) { nav.push(Route.Search(tag = t)) } }
                }
            }
            Spacer(Modifier.height(48.dp))
        }
    }

    fullPhoto?.let { p ->
        Dialog(onDismissRequest = { fullPhoto = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(Color.Black).clickable { fullPhoto = null }) {
                AsyncImage(model = File(Store.mediaDir, p), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                IconButton(onClick = { fullPhoto = null }, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
                    Icon(Icons.Filled.Close, "Close", tint = Color.White)
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this entry?") },
            text = { Text("The entry, its photos and its voice notes will be permanently removed.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; Store.deleteEntry(e.id); nav.pop() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
fun VoiceClipRow(clip: AudioClip, label: String, onRemove: (() -> Unit)? = null) {
    var playing by remember { mutableStateOf(VoicePlayer.playingFile == clip.file) }
    DisposableEffect(clip.file) { onDispose { if (VoicePlayer.playingFile == clip.file) VoicePlayer.stop() } }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = {
            if (playing) VoicePlayer.stop()
            else { playing = true; VoicePlayer.play(File(Store.mediaDir, clip.file)) { playing = false } }
        }) {
            Icon(if (playing) Icons.Filled.Stop else Icons.Filled.PlayArrow, if (playing) "Stop" else "Play", tint = MaterialTheme.colorScheme.primary)
        }
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(formatDuration(clip.durationMs), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (onRemove != null) IconButton(onClick = { if (playing) VoicePlayer.stop(); onRemove() }) { Icon(Icons.Filled.Close, "Remove voice note") }
    }
}

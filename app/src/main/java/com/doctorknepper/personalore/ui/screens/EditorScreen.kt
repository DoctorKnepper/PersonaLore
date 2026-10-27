package com.doctorknepper.personalore.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.doctorknepper.personalore.ui.EncryptedImage
import com.doctorknepper.personalore.ui.AppLock
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.doctorknepper.personalore.data.AudioClip
import com.doctorknepper.personalore.data.Entry
import com.doctorknepper.personalore.data.PromptLibrary
import com.doctorknepper.personalore.data.Store
import com.doctorknepper.personalore.ui.BackBar
import com.doctorknepper.personalore.ui.DayFmt
import com.doctorknepper.personalore.ui.LocalNav
import com.doctorknepper.personalore.ui.MoodPicker
import com.doctorknepper.personalore.ui.Route
import com.doctorknepper.personalore.ui.SectionLabel
import com.doctorknepper.personalore.ui.TagPill
import com.doctorknepper.personalore.util.VoiceRecorder
import com.doctorknepper.personalore.util.formatDuration
import kotlinx.coroutines.delay
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Questions that help you go deeper on anything you've written, used alongside a prompt's own follow-ups. */
val GenericFollowUps = listOf(
    "What feeling sits underneath what you just wrote?",
    "Why does this matter so much to you?",
    "What would you say to a friend who wrote this?",
    "What part of this have you never said out loud?",
    "How has this shaped who you are today?",
    "What do you want to do with this now?",
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(route: Route.Editor) {
    val nav = LocalNav.current
    val context = LocalContext.current
    val existing = remember { route.entryId?.let { Store.entry(it) } }
    val id = remember { existing?.id ?: Store.newId() }

    var date by remember { mutableStateOf(existing?.date ?: route.date ?: LocalDate.now()) }
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var body by remember { mutableStateOf(TextFieldValue(existing?.body ?: "")) }
    var mood by remember { mutableStateOf(existing?.mood) }
    val tags = remember { mutableStateListOf<String>().apply { addAll(existing?.tags ?: emptyList()) } }
    val photos = remember { mutableStateListOf<String>().apply { addAll(existing?.photos ?: emptyList()) } }
    val audio = remember { mutableStateListOf<AudioClip>().apply { addAll(existing?.audio ?: emptyList()) } }
    var promptId by remember { mutableStateOf(existing?.promptId ?: route.promptId) }
    val prompt = PromptLibrary.find(promptId, Store.promptState)
    var promptText by remember { mutableStateOf(existing?.promptText ?: prompt?.text) }
    val newMedia = remember { mutableListOf<String>() }

    var tagInput by remember { mutableStateOf("") }
    var showDate by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }

    val changed = existing == null && (title.isNotBlank() || body.text.isNotBlank() || photos.isNotEmpty() || audio.isNotEmpty() || mood != null || tags.isNotEmpty()) ||
        existing != null && (title != existing.title || body.text != existing.body || mood != existing.mood || tags.toList() != existing.tags ||
            photos.toList() != existing.photos || audio.toList() != existing.audio || date != existing.date || promptId != existing.promptId)
    val canSave = body.text.isNotBlank() || title.isNotBlank() || photos.isNotEmpty() || audio.isNotEmpty()

    // voice recording
    val recorder = remember { VoiceRecorder(context) }
    var recording by remember { mutableStateOf(false) }
    var recStart by remember { mutableLongStateOf(0L) }
    var recElapsed by remember { mutableLongStateOf(0L) }
    LaunchedEffect(recording) { while (recording) { recElapsed = System.currentTimeMillis() - recStart; delay(250) } }
    DisposableEffect(Unit) { onDispose { if (recorder.isRecording) recorder.stop()?.let { (f, _) -> f.delete() } } }

    fun startRecording() {
        val f = Store.newTempAudioFile()
        if (recorder.start(f)) { recording = true; recStart = System.currentTimeMillis(); recElapsed = 0 }
    }
    fun stopRecording() {
        recording = false
        recorder.stop()?.let { (f, d) -> Store.adoptAudio(f)?.let { n -> audio.add(AudioClip(n, d)); newMedia.add(n) } }
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) startRecording() }
    val scope = rememberCoroutineScope()
    var importing by remember { mutableStateOf(0) }
    val pickPhotos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        importing += uris.size
        scope.launch {
            uris.forEach { u ->
                withContext(Dispatchers.IO) { Store.importPhoto(u) }?.let { photos.add(it); newMedia.add(it) }
                importing--
            }
        }
    }

    fun addTag(raw: String) {
        val t = raw.trim().trimStart('#').lowercase().replace(Regex("\\s+"), "-")
        if (t.isNotEmpty() && t !in tags) tags.add(t)
        tagInput = ""
    }

    fun save() {
        if (recording) stopRecording()
        if (tagInput.isNotBlank()) addTag(tagInput)
        val now = System.currentTimeMillis()
        val e = Entry(
            id = id, date = date, createdAt = existing?.createdAt ?: now, updatedAt = now,
            title = title.trim(), body = body.text.trimEnd(), mood = mood, tags = tags.toList(),
            photos = photos.toList(), audio = audio.toList(), promptId = promptId, promptText = if (promptId != null) promptText else null,
        )
        Store.saveEntry(e)
        if (existing == null) nav.replace(Route.View(id)) else nav.pop()
    }

    fun discard() {
        if (recording) { recorder.cancel(); recording = false }
        Store.discardMedia(newMedia)
        nav.pop()
    }

    BackHandler { if (changed) confirmDiscard = true else discard() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            BackBar(if (existing == null) "New entry" else "Edit entry", onBack = { if (changed) confirmDiscard = true else discard() }) {
                TextButton(onClick = ::save, enabled = canSave && importing == 0) { Text("Save", fontWeight = FontWeight.SemiBold) }
            }
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        ) {
            // Date
            Row(
                Modifier.clip(RoundedCornerShape(10.dp)).clickable { showDate = true }.padding(vertical = 6.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.CalendarToday, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(date.format(DayFmt), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }

            // Prompt
            if (promptId != null && promptText != null) {
                Spacer(Modifier.height(8.dp))
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.primaryContainer).padding(14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(6.dp))
                        Text(prompt?.category ?: "Prompt", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                        IconButton(onClick = { promptId = null; promptText = null }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Filled.Close, "Remove prompt", Modifier.size(16.dp))
                        }
                    }
                    Text(promptText!!, style = MaterialTheme.typography.titleMedium, fontStyle = FontStyle.Italic)
                }
                val followUps = (prompt?.followUps ?: emptyList()) + GenericFollowUps.shuffled(java.util.Random(id.hashCode().toLong())).take(2)
                Spacer(Modifier.height(8.dp))
                Text("Go deeper: tap a question to add it to your entry", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                    items(followUps) { q ->
                        Text(
                            q, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.width(210.dp).clip(RoundedCornerShape(12.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                                .clickable {
                                    val sep = if (body.text.isBlank()) "" else "\n\n"
                                    val newText = body.text.trimEnd() + sep + "› " + q + "\n"
                                    body = TextFieldValue(newText, TextRange(newText.length))
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        )
                    }
                }
            } else {
                Spacer(Modifier.height(4.dp))
                AssistChip(
                    onClick = {
                        PromptLibrary.random(Store.promptState)?.let { p -> promptId = p.id; promptText = p.text }
                    },
                    label = { Text("Give me a prompt") },
                    leadingIcon = { Icon(Icons.Outlined.Shuffle, null, Modifier.size(18.dp)) },
                )
            }

            val fieldColors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
            )
            TextField(
                value = title, onValueChange = { title = it }, placeholder = { Text("Title (optional)", style = MaterialTheme.typography.titleLarge) },
                textStyle = MaterialTheme.typography.titleLarge, singleLine = true, colors = fieldColors, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            TextField(
                value = body, onValueChange = { body = it }, placeholder = { Text("Write freely…") },
                textStyle = MaterialTheme.typography.bodyLarge, colors = fieldColors, modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            Text(
                "${body.text.split(Regex("\\s+")).count { it.isNotBlank() }} words",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp),
            )

            SectionLabel("Mood")
            MoodPicker(mood) { mood = it }

            SectionLabel("Tags")
            if (tags.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    tags.forEach { t -> TagPill(t, selected = true) { tags.remove(t) } }
                }
                Text("Tap a tag to remove it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                Spacer(Modifier.height(8.dp))
            }
            OutlinedTextField(
                value = tagInput,
                onValueChange = { v -> if (v.endsWith(",") || v.endsWith(" ")) addTag(v.dropLast(1)) else tagInput = v },
                placeholder = { Text("Add a tag") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { addTag(tagInput) }),
            )
            val suggestions = Store.allTags.filter { it !in tags && (tagInput.isBlank() || it.startsWith(tagInput.trim().trimStart('#').lowercase())) }.take(10)
            if (suggestions.isNotEmpty()) {
                FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    suggestions.forEach { t -> TagPill(t) { addTag(t) } }
                }
            }

            SectionLabel("Photos")
            if (importing > 0) Text("Adding $importing photo${if (importing == 1) "" else "s"}…", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(photos.toList()) { p ->
                    Box {
                        EncryptedImage(
                            p, maxSidePx = 300, contentScale = ContentScale.Crop,
                            modifier = Modifier.size(96.dp).clip(RoundedCornerShape(12.dp)),
                        )
                        Box(
                            Modifier.align(Alignment.TopEnd).padding(4.dp).size(24.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f))
                                .clickable { photos.remove(p) },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Filled.Close, "Remove photo", Modifier.size(14.dp), tint = Color.White) }
                    }
                }
                item {
                    Box(
                        Modifier.size(96.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable { AppLock.leavingForPicker(); pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Outlined.AddPhotoAlternate, "Add photos", tint = MaterialTheme.colorScheme.primary) }
                }
            }

            SectionLabel("Voice notes")
            audio.toList().forEachIndexed { i, clip ->
                VoiceClipRow(clip, label = "Voice note ${i + 1}", onRemove = { audio.remove(clip) },
                    onNoteChange = { n -> if (i < audio.size) audio[i] = audio[i].copy(note = n) })
            }
            if (recording) {
                OutlinedButton(onClick = ::stopRecording, modifier = Modifier.fillMaxWidth()) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error))
                    Spacer(Modifier.width(8.dp))
                    Text("Recording ${formatDuration(recElapsed)} · tap to stop")
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Filled.Stop, null)
                }
            } else {
                OutlinedButton(onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startRecording()
                    else { AppLock.leavingForPicker(); micPermission.launch(Manifest.permission.RECORD_AUDIO) }
                }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Mic, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Record a voice note")
                }
            }
            Spacer(Modifier.height(48.dp))
        }
    }

    if (showDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
            selectableDates = object : androidx.compose.material3.SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= System.currentTimeMillis() + 86_400_000
            },
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    showDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard changes?") },
            text = { Text("What you've written since the last save will be lost.") },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; discard() }) { Text("Discard") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep writing") } },
        )
    }
}

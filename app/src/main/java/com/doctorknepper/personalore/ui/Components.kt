package com.doctorknepper.personalore.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.doctorknepper.personalore.data.Entry
import com.doctorknepper.personalore.data.Moods
import com.doctorknepper.personalore.data.moodOf
import java.time.LocalDate
import java.time.format.DateTimeFormatter

val DayFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")
val ShortFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d")
val MonthFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")

fun LocalDate.friendly(): String = when (this) {
    LocalDate.now() -> "Today"
    LocalDate.now().minusDays(1) -> "Yesterday"
    else -> if (year == LocalDate.now().year) format(ShortFmt) else format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackBar(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

/** Header used at the top of each main tab. */
@Composable
fun TabHeader(title: String, subtitle: String? = null, actions: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        actions()
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(), modifier = modifier.padding(top = 18.dp, bottom = 8.dp),
        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold,
    )
}

@Composable
fun AppCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, highlight: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val colors = CardDefaults.cardColors(
        containerColor = if (highlight) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
    )
    val border = BorderStroke(1.dp, if (highlight) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant)
    if (onClick != null) Card(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = colors, border = border) {
        Column(Modifier.padding(16.dp), content = content)
    } else Card(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = colors, border = border) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EntryCard(e: Entry, onClick: () -> Unit, showDate: Boolean = true) {
    AppCard(onClick = onClick, modifier = Modifier.padding(vertical = 5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (showDate) Text(e.date.friendly(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                val title = e.title.ifBlank { e.promptText ?: e.preview.take(60).ifBlank { "Untitled entry" } }
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            }
            moodOf(e.mood)?.let { Text(it.emoji, style = MaterialTheme.typography.headlineSmall) }
        }
        if (e.body.isNotBlank() && (e.title.isNotBlank() || e.promptText != null)) {
            Spacer(Modifier.height(6.dp))
            Text(e.preview, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        if (e.tags.isNotEmpty() || e.photos.isNotEmpty() || e.audio.isNotEmpty() || e.promptId != null) {
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (e.promptId != null) MetaChip(Icons.Outlined.AutoAwesome, "Prompt")
                if (e.photos.isNotEmpty()) MetaChip(Icons.Outlined.Image, e.photos.size.toString())
                if (e.audio.isNotEmpty()) MetaChip(Icons.Outlined.Mic, e.audio.size.toString())
                e.tags.take(4).forEach { TagPill(it) }
            }
        }
    }
}

@Composable
fun MetaChip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun TagPill(tag: String, selected: Boolean = false, onClick: (() -> Unit)? = null) {
    val m = Modifier.clip(RoundedCornerShape(50))
        .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer)
        .let { if (onClick != null) it.clickable(onClick = onClick) else it }
        .padding(horizontal = 10.dp, vertical = 4.dp)
    Text("#$tag", m, style = MaterialTheme.typography.labelMedium,
        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer)
}

@Composable
fun MoodPicker(selected: Int?, onSelect: (Int?) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Moods.forEach { m ->
            val on = selected == m.value
            Column(
                Modifier.clip(RoundedCornerShape(14.dp))
                    .then(if (on) Modifier.background(MaterialTheme.colorScheme.primaryContainer).border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp)) else Modifier)
                    .clickable { onSelect(if (on) null else m.value) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(m.emoji, style = MaterialTheme.typography.headlineSmall)
                Text(m.label, style = MaterialTheme.typography.labelSmall,
                    color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

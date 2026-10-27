package com.doctorknepper.personalore.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.doctorknepper.personalore.data.Prompt
import com.doctorknepper.personalore.data.PromptLibrary
import com.doctorknepper.personalore.data.Store
import com.doctorknepper.personalore.ui.AppCard
import com.doctorknepper.personalore.ui.EmptyState
import com.doctorknepper.personalore.ui.LocalNav
import com.doctorknepper.personalore.ui.Route
import com.doctorknepper.personalore.ui.SectionLabel
import com.doctorknepper.personalore.ui.TabHeader
import com.doctorknepper.personalore.ui.friendly
import androidx.compose.foundation.horizontalScroll
import java.time.LocalDate

private enum class PromptTab(val label: String) { CATEGORIES("Categories"), SERIES("Guided series"), FAVORITES("Favorites"), MINE("My prompts") }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PromptsScreen() {
    val nav = LocalNav.current
    val st = Store.promptState
    var tab by rememberSaveable { mutableStateOf(PromptTab.CATEGORIES) }
    var surprise by remember { mutableStateOf(PromptLibrary.random(st)) }
    var showAdd by remember { mutableStateOf(false) }
    val all = PromptLibrary.allPrompts(st)
    val answeredCount = all.count { (st.answered[it.id] ?: 0) > 0 }

    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
        item { TabHeader("Prompts", "$answeredCount of ${all.size} answered") }
        item {
            AppCard(highlight = true) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("SURPRISE ME · ${surprise?.category?.uppercase() ?: ""}", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    surprise?.let { p -> FavoriteButton(p.id) }
                }
                Text(surprise?.text ?: "You've hidden every prompt. Unhide some in a category.", style = MaterialTheme.typography.titleLarge, fontStyle = FontStyle.Italic)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { surprise?.let { nav.push(Route.Editor(promptId = it.id)) } }, enabled = surprise != null) { Text("Write") }
                    OutlinedButton(onClick = { surprise = PromptLibrary.random(Store.promptState, exclude = surprise?.id) }) {
                        Icon(Icons.Outlined.Shuffle, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Another")
                    }
                    TextButton(onClick = {
                        surprise?.let { Store.toggleHidden(it.id) }
                        surprise = PromptLibrary.random(Store.promptState, exclude = surprise?.id)
                    }) { Text("Skip for good") }
                }
            }
        }
        item {
            Row(Modifier.padding(top = 16.dp, bottom = 4.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PromptTab.entries.forEach { t -> FilterChip(selected = tab == t, onClick = { tab = t }, label = { Text(t.label) }) }
            }
        }
        when (tab) {
            PromptTab.CATEGORIES -> items(PromptLibrary.allCategoryNames(st)) { name ->
                val ps = PromptLibrary.promptsIn(name, st)
                val done = ps.count { (st.answered[it.id] ?: 0) > 0 }
                AppCard(onClick = { nav.push(Route.Category(name)) }, modifier = Modifier.padding(vertical = 5.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("$done of ${ps.size} answered", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(progress = { if (ps.isEmpty()) 0f else done / ps.size.toFloat() }, modifier = Modifier.fillMaxWidth())
                        }
                        Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            PromptTab.SERIES -> items(PromptLibrary.series) { s ->
                val started = st.seriesStarted[s.id]
                val done = s.prompts.count { (st.answered[it.id] ?: 0) > 0 }
                AppCard(onClick = { nav.push(Route.Series(s.id)) }, modifier = Modifier.padding(vertical = 5.dp), highlight = started != null && done < s.prompts.size) {
                    Text(s.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(s.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        when {
                            done == s.prompts.size -> "Completed"
                            started != null -> "In progress · day ${seriesDay(started, s.prompts.size)} · $done of ${s.prompts.size} written"
                            else -> "${s.prompts.size} days"
                        },
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            PromptTab.FAVORITES -> {
                val favs = st.favorites.mapNotNull { PromptLibrary.find(it, st) }
                if (favs.isEmpty()) item { EmptyState("No favorites yet", "Tap the star on any prompt to save it here.") }
                items(favs, key = { it.id }) { p -> PromptRow(p) }
            }
            PromptTab.MINE -> {
                item {
                    Button(onClick = { showAdd = true }, modifier = Modifier.padding(vertical = 6.dp)) {
                        Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text("Write your own prompt")
                    }
                }
                if (st.custom.isEmpty()) item { EmptyState("No prompts of your own yet", "Add questions you want to come back to. They'll show up in their category, the daily prompt and Surprise me.") }
                items(st.custom, key = { it.id }) { c -> PromptRow(c.asPrompt(), showCategory = true, onDelete = { Store.deleteCustomPrompt(c.id) }) }
            }
        }
    }
    if (showAdd) AddPromptDialog(initialCategory = null, onDismiss = { showAdd = false })
}

fun seriesDay(startEpochDay: Long, length: Int): Int =
    (LocalDate.now().toEpochDay() - startEpochDay + 1).toInt().coerceIn(1, length)

@Composable
fun FavoriteButton(id: String) {
    val fav = id in Store.promptState.favorites
    IconButton(onClick = { Store.toggleFavorite(id) }, modifier = Modifier.size(36.dp)) {
        Icon(if (fav) Icons.Filled.Star else Icons.Outlined.StarOutline, if (fav) "Unfavorite" else "Favorite", Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
    }
}

/** One prompt in a list: tap to write, star to favorite, hide or delete. */
@Composable
fun PromptRow(p: Prompt, showCategory: Boolean = true, onDelete: (() -> Unit)? = null) {
    val nav = LocalNav.current
    val st = Store.promptState
    val answered = st.answered[p.id] ?: 0
    val hidden = p.id in st.hidden
    AppCard(onClick = { nav.push(Route.Editor(promptId = p.id)) }, modifier = Modifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                if (showCategory) Text(p.category, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text(p.text, style = MaterialTheme.typography.bodyLarge,
                    color = if (hidden) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                if (answered > 0 || hidden) Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (answered > 0) {
                        Icon(Icons.Filled.CheckCircle, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(4.dp))
                        Text(if (answered == 1) "Answered" else "Answered $answered times", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                    }
                    if (hidden) Text("Hidden from random picks", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column {
                FavoriteButton(p.id)
                if (onDelete != null) IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) { Icon(Icons.Outlined.Delete, "Delete prompt", Modifier.size(20.dp)) }
                else IconButton(onClick = { Store.toggleHidden(p.id) }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Outlined.VisibilityOff, if (hidden) "Unhide" else "Hide", Modifier.size(20.dp),
                        tint = if (hidden) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPromptDialog(initialCategory: String?, onDismiss: () -> Unit) {
    val st = Store.promptState
    var text by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(initialCategory ?: "My Questions") }
    var expanded by remember { mutableStateOf(false) }
    val options = (PromptLibrary.allCategoryNames(st) + "My Questions").distinct()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Your own prompt") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(text, { text = it }, label = { Text("Question") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = category, onValueChange = { category = it }, label = { Text("Category (pick or type a new one)") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, singleLine = true,
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable).fillMaxWidth(),
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        options.forEach { o -> DropdownMenuItem(text = { Text(o) }, onClick = { category = o; expanded = false }) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank() && category.isNotBlank(), onClick = {
                Store.addCustomPrompt(category.trim(), text.trim()); onDismiss()
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun CategoryScreen(name: String) {
    val nav = LocalNav.current
    val st = Store.promptState
    var showHidden by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    val prompts = PromptLibrary.promptsIn(name, st)
    val visible = prompts.filter { showHidden || it.id !in st.hidden }
    val hiddenCount = prompts.count { it.id in st.hidden }
    androidx.compose.material3.Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { com.doctorknepper.personalore.ui.BackBar(name, onBack = { nav.pop() }) },
        floatingActionButton = {
            androidx.compose.material3.FloatingActionButton(onClick = { showAdd = true }, containerColor = MaterialTheme.colorScheme.primary) {
                Icon(Icons.Filled.Add, "Add a prompt")
            }
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp)) {
            item {
                Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        PromptLibrary.random(Store.promptState, categoryName = name)?.let { nav.push(Route.Editor(promptId = it.id)) }
                    }) { Icon(Icons.Outlined.Shuffle, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Random from here") }
                    if (hiddenCount > 0) FilterChip(selected = showHidden, onClick = { showHidden = !showHidden }, label = { Text("Show $hiddenCount hidden") })
                }
            }
            items(visible, key = { it.id }) { p -> PromptRow(p, showCategory = false, onDelete = if (p.custom) ({ Store.deleteCustomPrompt(p.id) }) else null) }
        }
    }
    if (showAdd) AddPromptDialog(initialCategory = name, onDismiss = { showAdd = false })
}

@Composable
fun SeriesScreen(id: String) {
    val nav = LocalNav.current
    val st = Store.promptState
    val s = PromptLibrary.series.first { it.id == id }
    val started = st.seriesStarted[id]
    val unlocked = if (started == null) 0 else seriesDay(started, s.prompts.size)
    androidx.compose.material3.Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { com.doctorknepper.personalore.ui.BackBar(s.title, onBack = { nav.pop() }) },
    ) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
            item {
                Text(s.description, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                if (started == null) {
                    Button(onClick = { Store.startSeries(id) }) { Text("Start today") }
                    Text("One new prompt unlocks each day. You can always catch up on days you missed.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Day $unlocked of ${s.prompts.size}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                        TextButton(onClick = { Store.stopSeries(id) }) { Text("Restart later") }
                    }
                }
                SectionLabel("Days")
            }
            items(s.prompts.withIndex().toList(), key = { it.value.id }) { (i, p) ->
                val open = i < unlocked
                val done = (st.answered[p.id] ?: 0) > 0
                AppCard(
                    onClick = if (open) ({ nav.push(Route.Editor(promptId = p.id)) }) else null,
                    modifier = Modifier.padding(vertical = 4.dp), highlight = open && !done && i == unlocked - 1,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (open) p.text else "Day ${i + 1} · unlocks ${if (started == null) "after you start" else LocalDate.ofEpochDay(started + i).friendly()}",
                            style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f),
                            color = if (open) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (done) Icon(Icons.Filled.CheckCircle, "Written", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

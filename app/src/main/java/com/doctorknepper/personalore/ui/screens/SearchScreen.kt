package com.doctorknepper.personalore.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.doctorknepper.personalore.data.Moods
import com.doctorknepper.personalore.data.Store
import com.doctorknepper.personalore.ui.BackBar
import com.doctorknepper.personalore.ui.EmptyState
import com.doctorknepper.personalore.ui.EntryCard
import com.doctorknepper.personalore.ui.LocalNav
import com.doctorknepper.personalore.ui.Route
import com.doctorknepper.personalore.ui.SectionLabel
import com.doctorknepper.personalore.ui.TagPill

@Composable
fun SearchScreen(initialTag: String?) {
    val nav = LocalNav.current
    var query by remember { mutableStateOf("") }
    val tags = remember { mutableStateListOf<String>().apply { initialTag?.let { add(it) } } }
    var mood by remember { mutableStateOf<Int?>(null) }
    var withMedia by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (initialTag == null) runCatching { focus.requestFocus() } }

    val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
    val results = Store.entries.filter { e ->
        val hay = e.searchText // title, body, prompt, tags and voice note labels
        words.all { it in hay } && tags.all { it in e.tags } && (mood == null || e.mood == mood) &&
            (!withMedia || e.photos.isNotEmpty() || e.audio.isNotEmpty())
    }
    val filtering = words.isNotEmpty() || tags.isNotEmpty() || mood != null || withMedia

    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { BackBar("Search", onBack = { nav.pop() }) }) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
            item {
                OutlinedTextField(
                    query, { query = it }, placeholder = { Text("Search entries and voice note labels") }, singleLine = true,
                    leadingIcon = { Icon(Icons.Outlined.Search, null) }, modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                Row(Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Moods.forEach { m -> FilterChip(selected = mood == m.value, onClick = { mood = if (mood == m.value) null else m.value }, label = { Text(m.emoji) }) }
                    FilterChip(selected = withMedia, onClick = { withMedia = !withMedia }, label = { Text("Photos or voice") })
                }
                val allTags = Store.allTags
                if (allTags.isNotEmpty()) {
                    SectionLabel("Filter by tag")
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        allTags.forEach { t -> TagPill(t, selected = t in tags) { if (t in tags) tags.remove(t) else tags.add(t) } }
                    }
                }
                if (filtering) SectionLabel("${results.size} ${if (results.size == 1) "entry" else "entries"}")
            }
            if (filtering && results.isEmpty()) item { EmptyState("No matches", "Try fewer words or remove a filter.") }
            if (filtering) items(results, key = { it.id }) { e -> EntryCard(e, onClick = { nav.push(Route.View(e.id)) }) }
        }
    }
}

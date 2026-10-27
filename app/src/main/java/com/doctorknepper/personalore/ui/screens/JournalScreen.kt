package com.doctorknepper.personalore.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.doctorknepper.personalore.data.Stats
import com.doctorknepper.personalore.data.Store
import com.doctorknepper.personalore.ui.AppCard
import com.doctorknepper.personalore.ui.EmptyState
import com.doctorknepper.personalore.ui.EntryCard
import com.doctorknepper.personalore.ui.LocalNav
import com.doctorknepper.personalore.ui.MonthFmt
import com.doctorknepper.personalore.ui.Route
import com.doctorknepper.personalore.ui.SectionLabel
import com.doctorknepper.personalore.ui.TabHeader
import java.time.LocalDate

@Composable
fun JournalScreen() {
    val nav = LocalNav.current
    val entries = Store.entries
    val streak = Stats.currentStreak(entries)
    val wroteToday = entries.any { it.date == LocalDate.now() }
    val daily = Store.dailyPrompt
    val onThisDay = Stats.onThisDay(entries)
    val byMonth = entries.groupBy { it.date.withDayOfMonth(1) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp)) {
            item {
                TabHeader("PersonaLore", LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("EEEE, MMMM d"))) {
                    IconButton(onClick = { nav.push(Route.Search()) }) { Icon(Icons.Outlined.Search, "Search") }
                    IconButton(onClick = { nav.push(Route.Settings) }) { Icon(Icons.Outlined.Settings, "Settings") }
                }
            }
            item {
                Row(Modifier.padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.LocalFireDepartment, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        when {
                            streak == 0 -> "Start a writing streak today"
                            wroteToday -> "$streak-day streak. You've written today."
                            else -> "$streak-day streak. Write today to keep it going."
                        },
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (daily != null) item {
                Spacer(Modifier.height(8.dp))
                AppCard(highlight = true) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("TODAY'S PROMPT · ${daily.category.uppercase()}", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        IconButton(onClick = { Store.rerollDailyPrompt() }) { Icon(Icons.Outlined.Shuffle, "Different prompt", tint = MaterialTheme.colorScheme.primary) }
                    }
                    Text(daily.text, style = MaterialTheme.typography.titleLarge, fontStyle = FontStyle.Italic)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { nav.push(Route.Editor(promptId = daily.id)) }) { Text("Write about this") }
                }
            }
            if (onThisDay.isNotEmpty()) {
                item { SectionLabel("On this day") }
                items(onThisDay, key = { "otd" + it.id }) { e ->
                    val yrs = LocalDate.now().year - e.date.year
                    Text(if (yrs == 1) "1 year ago" else "$yrs years ago", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    EntryCard(e, onClick = { nav.push(Route.View(e.id)) }, showDate = false)
                }
            }
            if (entries.isEmpty()) item {
                Spacer(Modifier.height(24.dp))
                EmptyState("Your story starts here", "Tap New entry to write your first page, or open Prompts for something to write about.")
            }
            byMonth.forEach { (month, list) ->
                item(key = "m$month") { SectionLabel(month.format(MonthFmt)) }
                items(list, key = { it.id }) { e -> EntryCard(e, onClick = { nav.push(Route.View(e.id)) }) }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { nav.push(Route.Editor()) },
            icon = { Icon(Icons.Filled.Add, null) },
            text = { Text("New entry") },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
}

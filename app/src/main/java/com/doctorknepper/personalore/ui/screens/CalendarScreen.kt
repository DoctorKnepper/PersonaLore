package com.doctorknepper.personalore.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.doctorknepper.personalore.data.Store
import com.doctorknepper.personalore.data.moodOf
import com.doctorknepper.personalore.ui.DayFmt
import com.doctorknepper.personalore.ui.EntryCard
import com.doctorknepper.personalore.ui.LocalNav
import com.doctorknepper.personalore.ui.MonthFmt
import com.doctorknepper.personalore.ui.Route
import com.doctorknepper.personalore.ui.SectionLabel
import com.doctorknepper.personalore.ui.TabHeader
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun CalendarScreen() {
    val nav = LocalNav.current
    var monthKey by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var selectedKey by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val month = YearMonth.parse(monthKey)
    val selected = LocalDate.parse(selectedKey)
    val byDay = Store.entries.groupBy { it.date }
    val dayEntries = byDay[selected].orEmpty()

    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
        item { TabHeader("Calendar", "${Store.entries.count { YearMonth.from(it.date) == month }} entries in ${month.format(MonthFmt)}") }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { monthKey = month.minusMonths(1).toString() }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Previous month") }
                Text(month.format(MonthFmt), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                IconButton(onClick = { monthKey = month.plusMonths(1).toString() }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Next month") }
            }
            if (month != YearMonth.now()) TextButton(onClick = { monthKey = YearMonth.now().toString(); selectedKey = LocalDate.now().toString() }) { Text("Back to today") }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach {
                    Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val lead = month.atDay(1).dayOfWeek.value - 1
            val cells = (0 until lead).map { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { d ->
                        Box(Modifier.weight(1f).aspectRatio(0.85f).padding(2.dp)) {
                            if (d != null) DayCell(d, byDay[d].orEmpty().size, byDay[d]?.mapNotNull { it.mood }?.average()?.takeIf { !it.isNaN() },
                                isSelected = d == selected) { selectedKey = d.toString() }
                        }
                    }
                    repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        item {
            SectionLabel(selected.format(DayFmt))
            if (dayEntries.isEmpty()) {
                Text("Nothing written this day.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
            }
            if (!selected.isAfter(LocalDate.now())) OutlinedButton(onClick = { nav.push(Route.Editor(date = selected)) }) {
                Icon(Icons.Filled.Add, null); Text(if (dayEntries.isEmpty()) "Write for this day" else "Add another entry")
            }
        }
        items(dayEntries, key = { it.id }) { e -> EntryCard(e, onClick = { nav.push(Route.View(e.id)) }, showDate = false) }
    }
}

@Composable
private fun DayCell(d: LocalDate, count: Int, mood: Double?, isSelected: Boolean, onClick: () -> Unit) {
    val today = d == LocalDate.now()
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) cs.primary else if (count > 0) cs.primaryContainer else cs.surfaceContainerLow)
            .then(if (today && !isSelected) Modifier.border(1.dp, cs.primary, RoundedCornerShape(10.dp)) else Modifier)
            .clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Text(d.dayOfMonth.toString(), style = MaterialTheme.typography.labelLarge, fontWeight = if (today) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) cs.onPrimary else if (count > 0) cs.primary else cs.onSurfaceVariant)
        if (mood != null) Text(moodOf(Math.round(mood).toInt())?.emoji ?: "", style = MaterialTheme.typography.labelSmall)
        else if (count > 0) Box(Modifier.padding(top = 4.dp).size(5.dp).clip(CircleShape).background(if (isSelected) cs.onPrimary else cs.primary))
    }
}

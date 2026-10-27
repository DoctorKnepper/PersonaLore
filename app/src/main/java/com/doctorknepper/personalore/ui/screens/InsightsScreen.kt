package com.doctorknepper.personalore.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.doctorknepper.personalore.data.Moods
import com.doctorknepper.personalore.data.PromptLibrary
import com.doctorknepper.personalore.data.Stats
import com.doctorknepper.personalore.data.Store
import com.doctorknepper.personalore.ui.AppCard
import com.doctorknepper.personalore.ui.EmptyState
import com.doctorknepper.personalore.ui.LocalNav
import com.doctorknepper.personalore.ui.Route
import com.doctorknepper.personalore.ui.SectionLabel
import com.doctorknepper.personalore.ui.TabHeader
import com.doctorknepper.personalore.ui.TagPill
import java.time.LocalDate

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InsightsScreen() {
    val nav = LocalNav.current
    val entries = Store.entries
    var range by rememberSaveable { mutableIntStateOf(30) }

    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
        item { TabHeader("Insights", "Patterns in what you write and how you feel") }
        if (entries.isEmpty()) {
            item { EmptyState("Nothing to chart yet", "Your streaks, mood trends and favorite topics will appear here once you start writing.") }
            return@LazyColumn
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Current streak", "${Stats.currentStreak(entries)}", "days", Modifier.weight(1f))
                StatTile("Longest streak", "${Stats.longestStreak(entries)}", "days", Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Entries", "${entries.size}", "${Stats.days(entries).size} days", Modifier.weight(1f))
                StatTile("Words", "%,d".format(Stats.wordCount(entries)), "written", Modifier.weight(1f))
            }
        }

        item {
            SectionLabel("Writing activity · last 15 weeks")
            ActivityGrid(Stats.days(entries))
        }

        item {
            SectionLabel("Mood over time")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(30 to "30 days", 90 to "90 days", 365 to "Year").forEach { (d, l) ->
                    FilterChip(selected = range == d, onClick = { range = d }, label = { Text(l) })
                }
            }
            Spacer(Modifier.height(8.dp))
            val from = LocalDate.now().minusDays(range.toLong() - 1)
            val points = Stats.dailyMood(entries).filterKeys { !it.isBefore(from) }.toSortedMap()
            AppCard {
                if (points.size < 2) Text("Add a mood to a few entries to see your trend.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else {
                    val avg = points.values.average()
                    Text("Average: ${Moods[(Math.round(avg).toInt() - 1).coerceIn(0, 4)].emoji} %.1f out of 5".format(avg), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    MoodChart(points.mapKeys { (it.key.toEpochDay() - from.toEpochDay()).toFloat() }, range.toFloat())
                }
            }
        }

        item {
            val withMood = entries.mapNotNull { it.mood }
            if (withMood.isNotEmpty()) {
                SectionLabel("How you've felt")
                AppCard {
                    val counts = Moods.associateWith { m -> withMood.count { it == m.value } }
                    val max = counts.values.max().coerceAtLeast(1)
                    Moods.reversed().forEach { m ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                            Text(m.emoji, Modifier.width(32.dp))
                            Text(m.label, Modifier.width(56.dp), style = MaterialTheme.typography.labelMedium)
                            Box(Modifier.weight(1f).height(14.dp).clip(RoundedCornerShape(7.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
                                Box(Modifier.fillMaxWidth(counts[m]!! / max.toFloat()).height(14.dp).clip(RoundedCornerShape(7.dp)).background(MaterialTheme.colorScheme.primary))
                            }
                            Text("${counts[m]}", Modifier.width(36.dp).padding(start = 8.dp), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }

        item {
            val tags = entries.flatMap { it.tags }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(15)
            if (tags.isNotEmpty()) {
                SectionLabel("Most-used tags")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    tags.forEach { (t, n) -> TagPill("$t · $n") { nav.push(Route.Search(tag = t)) } }
                }
            }
        }

        item {
            val st = Store.promptState
            val explored = PromptLibrary.allCategoryNames(st).map { c ->
                val ps = PromptLibrary.promptsIn(c, st); c to ps.count { (st.answered[it.id] ?: 0) > 0 }
            }.filter { it.second > 0 }.sortedByDescending { it.second }
            if (explored.isNotEmpty()) {
                SectionLabel("Topics you've explored")
                AppCard {
                    explored.forEach { (c, n) ->
                        Row(Modifier.padding(vertical = 3.dp)) {
                            Text(c, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Text("$n ${if (n == 1) "prompt" else "prompts"}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, sub: String, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(14.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 15 weeks x 7 days of squares, filled on days you wrote. */
@Composable
private fun ActivityGrid(days: Set<LocalDate>) {
    val today = LocalDate.now()
    val weeks = 15
    val start = today.minusDays((today.dayOfWeek.value - 1).toLong()).minusWeeks((weeks - 1).toLong())
    val on = MaterialTheme.colorScheme.primary
    val off = MaterialTheme.colorScheme.surfaceContainerHighest
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(weeks) { w ->
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(7) { d ->
                    val day = start.plusDays((w * 7 + d).toLong())
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(3.dp))
                            .background(if (day.isAfter(today)) off.copy(alpha = 0.3f) else if (day in days) on else off),
                    )
                }
            }
        }
    }
}

@Composable
private fun MoodChart(points: Map<Float, Double>, span: Float) {
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    Row {
        Column(Modifier.height(160.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Moods.reversed().forEach { Text(it.emoji, style = MaterialTheme.typography.labelSmall) }
        }
        Spacer(Modifier.width(8.dp))
        Canvas(Modifier.weight(1f).height(160.dp).padding(vertical = 8.dp)) {
            val w = size.width; val h = size.height
            fun y(m: Double) = (h - (m - 1) / 4.0 * h).toFloat()
            fun x(d: Float) = if (span <= 1) 0f else d / (span - 1) * w
            for (m in 1..5) drawLine(grid, Offset(0f, y(m.toDouble())), Offset(w, y(m.toDouble())), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            val path = Path()
            points.entries.forEachIndexed { i, (d, m) -> if (i == 0) path.moveTo(x(d), y(m)) else path.lineTo(x(d), y(m)) }
            drawPath(path, line, style = Stroke(width = 4f, cap = StrokeCap.Round))
            points.forEach { (d, m) -> drawCircle(line, 6f, Offset(x(d), y(m))) }
        }
    }
}

package com.doctorknepper.personalore.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.doctorknepper.personalore.data.Entry
import com.doctorknepper.personalore.data.Store
import com.doctorknepper.personalore.ui.BackBar
import com.doctorknepper.personalore.ui.EmptyState
import com.doctorknepper.personalore.ui.EntryCard
import com.doctorknepper.personalore.ui.LocalNav
import com.doctorknepper.personalore.ui.Snacks

/** Deleted entries wait here for 30 days before they're gone for good. */
@Composable
fun TrashScreen() {
    val nav = LocalNav.current
    var confirmEmpty by remember { mutableStateOf(false) }
    var confirmOne by remember { mutableStateOf<Entry?>(null) }
    val items = Store.trash

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            BackBar("Trash", onBack = { nav.pop() }) {
                if (items.isNotEmpty()) TextButton(onClick = { confirmEmpty = true }) { Text("Empty", color = MaterialTheme.colorScheme.error) }
            }
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
            item {
                Text("Deleted entries are kept for ${Store.TRASH_DAYS} days, then removed for good along with their photos and voice notes.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
            }
            if (items.isEmpty()) item { EmptyState("Trash is empty", "Nothing has been deleted in the last ${Store.TRASH_DAYS} days.") }
            items(items, key = { it.id }) { e ->
                val daysLeft = (Store.TRASH_DAYS - (System.currentTimeMillis() - (e.deletedAt ?: 0)) / 86_400_000).coerceAtLeast(0)
                EntryCard(e, onClick = {})
                Row(Modifier.padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { Store.restoreEntry(e.id); Snacks.show("Entry restored.") }) { Text("Restore") }
                    TextButton(onClick = { confirmOne = e }) { Text("Delete forever", color = MaterialTheme.colorScheme.error) }
                    Text("$daysLeft days left", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 14.dp))
                }
            }
        }
    }

    if (confirmEmpty) AlertDialog(
        onDismissRequest = { confirmEmpty = false },
        title = { Text("Empty the trash?") },
        text = { Text("${items.size} entries and their photos and voice notes will be permanently deleted.") },
        confirmButton = { TextButton(onClick = { confirmEmpty = false; Store.emptyTrash() }) { Text("Delete forever", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmEmpty = false }) { Text("Cancel") } },
    )
    confirmOne?.let { e ->
        AlertDialog(
            onDismissRequest = { confirmOne = null },
            title = { Text("Delete forever?") },
            text = { Text("This entry and its photos and voice notes will be permanently deleted.") },
            confirmButton = { TextButton(onClick = { confirmOne = null; Store.deleteForever(e.id) }) { Text("Delete forever", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmOne = null }) { Text("Cancel") } },
        )
    }
}

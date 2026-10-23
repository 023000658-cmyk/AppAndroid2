package com.example.appandroid2

import android.content.SharedPreferences
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Una entrada del historial. kind = "calc" (operación) o "eq" (ecuación resuelta). */
data class HistoryItem(val kind: String, val input: String, val output: String) {

    private fun encode(): String = listOf(kind, input, output).joinToString(FIELD)

    companion object {
        private const val FIELD = "\u001F"
        private const val ITEM = "\u001E"
        private const val KEY = "history"
        const val MAX_ITEMS = 50

        private fun decode(s: String): HistoryItem? {
            val p = s.split(FIELD)
            return if (p.size == 3) HistoryItem(p[0], p[1], p[2]) else null
        }

        fun load(prefs: SharedPreferences): List<HistoryItem> =
            (prefs.getString(KEY, "") ?: "").split(ITEM).mapNotNull { decode(it) }

        fun save(prefs: SharedPreferences, items: List<HistoryItem>) {
            prefs.edit().putString(KEY, items.joinToString(ITEM) { it.encode() }).apply()
        }
    }
}

@Composable
fun HistoryDialog(
    entries: List<HistoryItem>,
    onSelect: (HistoryItem) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Historial") },
        text = {
            if (entries.isEmpty()) {
                Text("Aún no hay operaciones guardadas.")
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                    items(entries) { item ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(item) }
                                .padding(vertical = 10.dp)
                        ) {
                            Text(
                                text = item.input,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp
                            )
                            Text(
                                text = if (item.kind == "calc") "= ${item.output}" else item.output,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 20.sp
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
        dismissButton = {
            if (entries.isNotEmpty()) {
                TextButton(onClick = onClear) { Text("Borrar todo") }
            }
        }
    )
}
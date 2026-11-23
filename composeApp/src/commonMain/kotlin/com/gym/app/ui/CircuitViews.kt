package com.gym.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gym.app.CatalogExercise
import com.gym.app.Circuit

/** "4 moves · 3 rounds" */
fun Circuit.summary(): String =
    (if (exercises.size == 1) "1 move" else "${exercises.size} moves") + if (rounds > 1) " · $rounds rounds" else ""

/**
 * A plan's Warm-up or Mobility section: its moves (in circuit order) and how many rounds. Empty, it's
 * just an "Add" button so plans without one stay tidy.
 */
@Composable
fun CircuitEditor(title: String, hint: String, circuit: Circuit, onChange: (Circuit) -> Unit, onAdd: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 12.dp, bottom = 4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (circuit.isEmpty) {
                Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Stepper("Rounds", circuit.rounds, 1..10) { onChange(circuit.copy(rounds = it)) }
                circuit.exercises.forEachIndexed { i, name ->
                    CircuitRow(name) {
                        if (i > 0) IconButton(onClick = {
                            onChange(circuit.copy(exercises = circuit.exercises.toMutableList().apply { add(i - 1, removeAt(i)) }))
                        }) { Icon(Icons.Default.ArrowUpward, "Move $name up") }
                        IconButton(onClick = {
                            onChange(circuit.copy(exercises = circuit.exercises.filterIndexed { j, _ -> j != i }))
                        }) { Icon(Icons.Default.Close, "Remove $name") }
                    }
                }
            }
            TextButton(onClick = onAdd) {
                Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (circuit.isEmpty) "Add ${title.lowercase()}" else "Add more")
            }
        }
    }
}

/** A workout's Warm-up or Mobility: the moves to go through, each with its photo and how-to. */
@Composable
fun CircuitCard(title: String, circuit: Circuit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (circuit.rounds > 1) {
                    Text(
                        "${circuit.rounds} rounds",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            circuit.exercises.forEach { CircuitRow(it) {} }
        }
    }
}

/** One move: photo (tap for how-to) and name, plus any [actions] on the right. */
@Composable
private fun CircuitRow(name: String, actions: @Composable () -> Unit) {
    val entry: CatalogExercise? = findExercise(name)
    var showDetails by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth()
            .then(if (entry != null) Modifier.clickable { showDetails = true } else Modifier)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExerciseThumbnail(entry, 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            entry?.kind?.let {
                Text(it.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        actions()
    }
    if (showDetails && entry != null) ExerciseDetailDialog(entry, onDismiss = { showDetails = false })
}

package com.gym.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gym.app.GymLocation
import com.gym.app.GymViewModel
import com.gym.app.WorkoutExercise
import androidx.compose.ui.semantics.contentDescription

/* An exercise's setup line and dialog (type, brand, gym) and per-person machine notes. */

/** "Isometric · gym80 · at Hotel gym", or a faint "Setup" prompt when nothing is set. */
@Composable
internal fun SetupLine(isometric: Boolean, brand: String?, otherGym: String?, onClick: () -> Unit) {
    val parts = listOfNotNull("Isometric".takeIf { isometric }, brand, otherGym?.let { "at $it" })
    Row(
        Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (otherGym != null) Icons.Default.Place else Icons.Default.Tune,
            null,
            Modifier.size(14.dp),
            tint = if (parts.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            else MaterialTheme.colorScheme.tertiary,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            parts.joinToString(" · ").ifEmpty { "Setup: type, machine, gym" },
            style = MaterialTheme.typography.labelMedium,
            color = if (parts.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            else MaterialTheme.colorScheme.tertiary,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ExerciseSetupDialog(
    vm: GymViewModel,
    gyms: List<GymLocation>,
    workoutGymId: String,
    exercise: WorkoutExercise,
    onDismiss: () -> Unit,
    onSave: (gymId: String?, brand: String?, isometric: Boolean) -> Unit,
) {
    var gymId by remember { mutableStateOf(exercise.gymId ?: workoutGymId) }
    var isometric by remember { mutableStateOf(exercise.isometric) }
    var brand by remember { mutableStateOf(exercise.brand) }
    val brandOptions = remember(gymId, gyms) { vm.gyms.brandOptions(gymId) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(exercise.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Type", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(!isometric, onClick = { isometric = false }, label = { Text("Normal") })
                    FilterChip(isometric, onClick = { isometric = true }, label = { Text("Isometric") })
                }
                Text("Machine brand", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(brand == null, onClick = { brand = null }, label = { Text("Not set") })
                    brandOptions.forEach { b ->
                        FilterChip(brand == b, onClick = { brand = b }, label = { Text(b) })
                    }
                }
                if (gyms.size > 1) {
                    Text("Done at", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                    gyms.forEach { g ->
                        GymRadioRow(
                            g.copy(name = if (g.id == workoutGymId) "${g.name} (this workout)" else g.name),
                            gymId == g.id,
                        ) { gymId = g.id }
                    }
                }
                Text(
                    "Weights are remembered separately for each gym, brand and type.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(gymId, brand, isometric) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** One line under a person's name: their saved machine settings, or a faint "Add note". */
@Composable
internal fun NoteLine(note: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.Notes,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = if (note != null) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            note ?: "Add note",
            style = MaterialTheme.typography.labelSmall,
            color = if (note != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun NoteDialog(title: String, subtitle: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(TextFieldValue(initial, TextRange(initial.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(title)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.copy(text = it.text.take(120)) },
                    placeholder = { Text("e.g. seat 4, back pad 2, handles middle") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                Text(
                    "Saved for this machine at this gym, and shown every time.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text.text) }) { Text("Save") } },
        dismissButton = {
            Row {
                if (initial.isNotEmpty()) TextButton(onClick = { onSave("") }) { Text("Clear") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

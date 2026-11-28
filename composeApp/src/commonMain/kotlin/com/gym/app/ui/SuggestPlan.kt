package com.gym.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gym.app.GymViewModel
import com.gym.app.MuscleLoad

/**
 * "Suggest a plan": shows the muscles trained least in the last 8 weeks and a plan for them, which
 * can be saved to Plans. [hasHistory] = there are finished workouts, so an empty progress list
 * just means it's still loading.
 */
@Composable
fun SuggestPlanDialog(vm: GymViewModel, hasHistory: Boolean, onDismiss: () -> Unit) {
    val progress by vm.progress.collectAsState()
    var both by remember { mutableStateOf(false) }
    var variant by remember { mutableIntStateOf(0) }
    val suggestion = remember(progress, both, variant) { vm.planSuggestion(progress, both, variant) }
    val loading = hasHistory && progress.isEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Suggested plan") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (vm.hasPartner()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !both, onClick = { both = false; variant = 0 }, label = { Text("Just me") })
                        FilterChip(selected = both, onClick = { both = true; variant = 0 }, label = { Text("Both of us") })
                    }
                }
                when {
                    loading -> Text("Looking at your workouts…")
                    suggestion == null -> Text(
                        "Finish a few workouts first. Suggestions come from the sets you've logged.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> {
                        Text(
                            "Least trained in the last ${suggestion.days / 7} weeks " +
                                "(${suggestion.workouts} workout${if (suggestion.workouts == 1) "" else "s"}):",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        suggestion.loads.forEach { LoadRow(it, it.muscle in suggestion.focus) }
                        HorizontalDivider()
                        Text(suggestion.plan.name, style = MaterialTheme.typography.titleMedium)
                        suggestion.plan.warmUp.takeUnless { it.isEmpty }?.let {
                            Text("Warm-up: ${it.summary()}", style = MaterialTheme.typography.bodySmall)
                        }
                        suggestion.plan.exercises.forEach { ex ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(ex.name, Modifier.weight(1f))
                                Text("${ex.sets} × ${ex.reps}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        suggestion.plan.mobility.takeUnless { it.isEmpty }?.let {
                            Text("Cool-down: ${it.summary()}", style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { variant++ }) {
                            Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Other exercises")
                        }
                        Text(
                            "Saved plans can be edited like any other.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { suggestion?.let { vm.saveSuggestedPlan(it.plan) }; onDismiss() },
                enabled = suggestion != null && !loading,
            ) { Text("Save plan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun LoadRow(load: MuscleLoad, focus: Boolean) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            load.muscle.label,
            Modifier.weight(1f),
            fontWeight = if (focus) FontWeight.Bold else FontWeight.Normal,
            color = if (focus) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            "${load.sets} set${if (load.sets == 1) "" else "s"}",
            fontWeight = if (focus) FontWeight.Bold else FontWeight.Normal,
            color = if (focus) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

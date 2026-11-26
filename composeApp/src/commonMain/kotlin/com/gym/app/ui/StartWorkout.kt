package com.gym.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gym.app.GymViewModel
import com.gym.app.Plan
import com.gym.app.Workout

/**
 * Starting a workout: "Who's training?" (both of you, or just me; only asked once you're paired),
 * then "Where are you training?" (only with more than one gym). Call [start] or [repeat], and put
 * [Dialogs] somewhere in the screen.
 */
class WorkoutStarter(private val vm: GymViewModel) {
    internal var askWho by mutableStateOf<((Boolean) -> Unit)?>(null)
    internal var askGym by mutableStateOf<Pair<String, (String) -> Unit>?>(null)

    private fun withWho(then: (solo: Boolean) -> Unit) {
        if (vm.hasPartner()) askWho = then else then(false)
    }

    private fun withGym(preselected: String, then: (gymId: String) -> Unit) {
        if (vm.gyms.list.value.size > 1) askGym = preselected to then else then(preselected)
    }

    fun start(plan: Plan?) = withWho { solo ->
        withGym(vm.homeGymId()) { gymId -> vm.startWorkout(plan, gymId, solo) }
    }

    fun repeat(w: Workout) = withWho { solo ->
        withGym(w.gymId ?: vm.homeGymId()) { gymId -> vm.repeatWorkout(w, gymId, solo) }
    }

    @Composable
    fun Dialogs() {
        val gyms by vm.gyms.list.collectAsState()
        askWho?.let { then ->
            val pair = vm.members()
            AlertDialog(
                onDismissRequest = { askWho = null },
                title = { Text("Who's training?") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { askWho = null; then(false) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Group, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("${vm.nameOf(pair[0])} & ${vm.nameOf(pair[1])}")
                        }
                        OutlinedButton(onClick = { askWho = null; then(true) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Person, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Just me")
                        }
                        Text(
                            "Just me adds only your sets. ${vm.nameOf(pair[1])} can still join later from the home screen.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                confirmButton = { TextButton(onClick = { askWho = null }) { Text("Cancel") } },
            )
        }
        askGym?.let { (preselected, then) ->
            ChooseGymDialog(
                gyms = gyms,
                selectedId = preselected,
                title = "Where are you training?",
                confirmLabel = "Start",
                onDismiss = { askGym = null },
                onChoose = { gymId -> askGym = null; then(gymId) },
            )
        }
    }
}

@Composable
fun rememberWorkoutStarter(vm: GymViewModel): WorkoutStarter = remember(vm) { WorkoutStarter(vm) }

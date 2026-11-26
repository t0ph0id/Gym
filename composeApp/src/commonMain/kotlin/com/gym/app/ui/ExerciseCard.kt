package com.gym.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gym.app.GymViewModel
import com.gym.app.WeightMemory
import com.gym.app.Workout
import com.gym.app.WorkoutExercise
import com.gym.app.WorkoutSet
import com.gym.app.formatWeight
import com.gym.app.nowMillis
import com.gym.app.positionOf
import com.gym.app.summary
import kotlinx.coroutines.delay

/* One exercise in a workout: both people's sets, its menu and dialogs, and the collapsed form. */

@Composable
internal fun ExerciseCard(
    vm: GymViewModel,
    workout: Workout,
    exercise: WorkoutExercise,
    sets: List<WorkoutSet>,
    members: List<String>,
    memory: WeightMemory,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onAllDone: () -> Unit,
) {
    var cardMenu by remember { mutableStateOf(false) }
    var editingNoteFor by remember { mutableStateOf<String?>(null) }
    val active = workout.finishedAt == null
    val catalogEntry = findExercise(exercise.name)
    var showDetails by remember { mutableStateOf(false) }
    var editingSetup by remember { mutableStateOf(false) }
    val gyms by vm.gyms.list.collectAsState()

    // U2: once every set is ticked, a running workout shows the exercise as one line (tap to open).
    val allDone = sets.isNotEmpty() && sets.all { it.done }
    var expanded by rememberSaveable(exercise.id) { mutableStateOf(false) }
    var wasDone by remember { mutableStateOf(allDone) }
    LaunchedEffect(allDone) {
        if (allDone && !wasDone) {
            expanded = false
            // Only scroll on for your own tick, not when your partner finishes theirs.
            if (sets.maxByOrNull { it.updatedAt }?.updatedBy == vm.uid) onAllDone()
        }
        wasDone = allDone
    }
    if (active && allDone && !expanded) {
        CollapsedExerciseCard(vm, exercise, sets, members, catalogEntry) { expanded = true }
        return
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ExerciseThumbnail(
                    catalogEntry,
                    44.dp,
                    onClick = catalogEntry?.let { { showDetails = true } },
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        exercise.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    SetupLine(
                        isometric = exercise.isometric,
                        brand = exercise.brand,
                        otherGym = exercise.gymId?.let { vm.gymName(it) },
                        onClick = { editingSetup = true },
                    )
                }
                Text(
                    "${exercise.targetSets} × ${exercise.targetReps}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box {
                    IconButton(onClick = { cardMenu = true }) { Icon(Icons.Default.MoreVert, "Exercise options") }
                    DropdownMenu(cardMenu, onDismissRequest = { cardMenu = false }) {
                        if (canMoveUp) DropdownMenuItem(
                            text = { Text("Move up") },
                            leadingIcon = { Icon(Icons.Default.ArrowUpward, null) },
                            onClick = { cardMenu = false; vm.moveExercise(workout, exercise.id, -1) },
                        )
                        if (active && allDone) DropdownMenuItem(
                            text = { Text("Collapse") },
                            leadingIcon = { Icon(Icons.Default.UnfoldLess, null) },
                            onClick = { cardMenu = false; expanded = false },
                        )
                        if (canMoveDown) DropdownMenuItem(
                            text = { Text("Move down") },
                            leadingIcon = { Icon(Icons.Default.ArrowDownward, null) },
                            onClick = { cardMenu = false; vm.moveExercise(workout, exercise.id, +1) },
                        )
                        DropdownMenuItem(
                            text = { Text("Remove exercise") },
                            leadingIcon = { Icon(Icons.Default.Delete, null) },
                            onClick = { cardMenu = false; vm.removeExercise(workout, exercise.id, sets) }, // Undo in the snackbar
                        )
                    }
                }
            }
            editingNoteFor?.let { member ->
                NoteDialog(
                    title = "${vm.nameOf(member)}'s settings",
                    subtitle = listOfNotNull(
                        exercise.name,
                        exercise.brand,
                        vm.gymName(vm.gymOf(workout, exercise)),
                    ).joinToString(" · "),
                    initial = memory.note(member, workout, exercise).orEmpty(),
                    onDismiss = { editingNoteFor = null },
                    onSave = { text -> editingNoteFor = null; vm.saveNote(member, workout, exercise, text) },
                )
            }
            if (showDetails && catalogEntry != null) {
                ExerciseDetailDialog(catalogEntry, onDismiss = { showDetails = false })
            }
            if (editingSetup) {
                ExerciseSetupDialog(
                    vm = vm,
                    gyms = gyms,
                    workoutGymId = workout.gymId ?: vm.homeGymId(),
                    exercise = exercise,
                    onDismiss = { editingSetup = false },
                    onSave = { gymId, brand, isometric ->
                        editingSetup = false
                        vm.changeExerciseSetup(workout, sets, exercise.id, gymId, brand, isometric)
                    },
                )
            }
            Spacer(Modifier.height(10.dp))
            val showLastTime = active && members.any { memory.lastTime(it, workout, exercise) != null }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                members.forEach { member ->
                    // Colour by person (you = primary), so it stays the same on a "Just me" workout.
                    val accent = if (member == vm.uid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                    val theirs = sets.filter { it.uid == member }.sortedBy { it.order }
                    // focusGroup: the keyboard's Next walks down this person's sets before the other column.
                    Column(Modifier.weight(1f).focusGroup(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        PersonLabel(vm.nameOf(member), accent)
                        // One line in every column (or none at all) so both people's set rows stay level.
                        if (showLastTime) {
                            Text(
                                "Last: " + (memory.lastTime(member, workout, exercise)?.summary() ?: "–"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        // U5: this person's machine settings for this exercise/gym/brand.
                        if (active) {
                            NoteLine(memory.note(member, workout, exercise)) { editingNoteFor = member }
                        }
                        theirs.forEach { s ->
                            key(s.id) {
                                SetRow(s, vm.nameOf(member), accent) { updated ->
                                    vm.updateSet(workout.id, updated)
                                    // U4: ticking a set off starts the rest timer on this phone.
                                    if (active && updated.done && !s.done) vm.rest.start()
                                }
                            }
                        }
                        Row {
                            TextButton(onClick = { vm.addSet(workout, exercise, member, theirs) }) { Text("+ Set", color = accent) }
                            if (theirs.isNotEmpty()) {
                                // Undo in the snackbar if it had anything in it.
                                TextButton(onClick = { vm.removeSet(workout.id, theirs.last()) }) {
                                    Text("− Set", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** "● Graciela · Leg press, set 3 · 2 min ago"; tap to jump there. Nothing if they've been idle a while. */
@Composable
internal fun PartnerPosition(name: String, workout: Workout, sets: List<WorkoutSet>, partner: String, onJump: (String) -> Unit) {
    var now by remember { mutableStateOf(nowMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(20_000); now = nowMillis() } }
    val pos = positionOf(partner, workout, sets, maxOf(now, nowMillis())) ?: return
    val minutes = ((now - pos.at) / 60_000).coerceAtLeast(0)
    val accent = MaterialTheme.colorScheme.tertiary
    Row(
        Modifier.clip(RoundedCornerShape(6.dp)).clickable { onJump(pos.exerciseId) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).background(accent, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(
            "$name · ${pos.exerciseName}, set ${pos.setNumber} · " + if (minutes < 1) "just now" else "$minutes min ago",
            style = MaterialTheme.typography.labelMedium,
            color = accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A finished exercise in a running workout: name and what each of you did, on one card line. */
@Composable
internal fun CollapsedExerciseCard(
    vm: GymViewModel,
    exercise: WorkoutExercise,
    sets: List<WorkoutSet>,
    members: List<String>,
    catalogEntry: com.gym.app.CatalogExercise?,
    onExpand: () -> Unit,
) {
    Card(
        onClick = onExpand,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f)),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            ExerciseThumbnail(catalogEntry, 32.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(exercise.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    members.mapNotNull { m ->
                        val theirs = sets.filter { it.uid == m }
                        if (theirs.isEmpty()) return@mapNotNull null
                        val top = theirs.mapNotNull { it.weight }.maxOrNull()
                        vm.nameOf(m) + " " + setsLabel(theirs.size) + (top?.let { " · ${formatWeight(it)} kg" } ?: "")
                    }.joinToString("   "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(28.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Check, "All sets done, tap to open", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
            }
        }
    }
}

package com.gym.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gym.app.GymViewModel
import com.gym.app.Loading
import com.gym.app.LocalAppPlatform
import com.gym.app.Screen
import com.gym.app.Workout
import com.gym.app.exerciseKey
import com.gym.app.formatDateTime
import com.gym.app.nowMillis
import com.gym.app.summary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutScreen(vm: GymViewModel, workoutId: String) {
    val found by remember(workoutId) { vm.workout(workoutId) }.collectAsState(null)
    val sets by remember(workoutId) { vm.sets(workoutId) }.collectAsState(emptyList())
    val partnership by vm.partnership.collectAsState()
    val members = remember(partnership) { vm.members() }
    val workout = found?.value
    val active = workout?.finishedAt == null

    var confirmFinish by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var addingExercise by remember { mutableStateOf(false) }
    var choosingGym by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var repeating by remember { mutableStateOf(false) }
    val workouts by vm.workouts.collectAsState()
    var menuOpen by remember { mutableStateOf(false) }
    val gyms by vm.gyms.list.collectAsState()
    val memory by vm.memory.collectAsState()

    val restEndsAt by vm.rest.endsAt.collectAsState()
    val platform = LocalAppPlatform.current
    // The screen stays on while the workout is running (phones on a bench time out mid-set).
    DisposableEffect(active, workout != null) {
        platform.keepScreenOn(active && workout != null)
        onDispose { platform.keepScreenOn(false) }
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    Scaffold(
        bottomBar = {
            restEndsAt?.let { end -> RestBar(end, onAdjust = vm.rest::adjust, onSkip = { vm.rest.stop() }) }
        },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(workout?.name ?: "Workout", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (workout != null) {
                            // One line, however big the phone's font is (it used to wrap next to Finish).
                            Text(
                                formatDateTime(workout.startedAt) + if (active) " · live" else "",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = vm::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (workout != null) {
                        if (active) {
                            Button(onClick = { confirmFinish = true }) { Text("Finish") }
                        }
                        Box {
                            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "Workout options") }
                            DropdownMenu(menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Rename") },
                                    leadingIcon = { Icon(Icons.Default.Edit, null) },
                                    onClick = { menuOpen = false; renaming = true },
                                )
                                if (!active) DropdownMenuItem(
                                    text = { Text("Repeat this workout") },
                                    leadingIcon = { Icon(Icons.Default.Replay, null) },
                                    onClick = { menuOpen = false; repeating = true },
                                )
                                DropdownMenuItem(
                                    text = { Text(if (active) "Discard workout" else "Delete workout") },
                                    leadingIcon = { Icon(Icons.Default.Delete, null) },
                                    onClick = {
                                        menuOpen = false
                                        // A running workout can be brought back with Undo; a finished one asks first.
                                        if (active) vm.discardWorkout(workout, sets) else confirmDelete = true
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { pad ->
        when {
            found == null -> Loading(Modifier.padding(pad))
            workout == null -> Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Text("This workout was deleted.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> {
                // Grouped once per change, not filtered again by every card.
                val setsByExercise = remember(sets) { sets.groupBy { it.exerciseId } }
                // Lazy index of the first exercise card: gym line, summary (finished), warm-up.
                val firstCard = 1 + (if (!active) 1 else 0) + (if (!workout.warmUp.isEmpty) 1 else 0)
                LazyColumn(
                Modifier.fillMaxSize().padding(pad).imePadding(),
                state = listState,
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Column {
                        AssistChip(
                            onClick = { if (gyms.size > 1) choosingGym = true else vm.navigate(Screen.Gyms) },
                            label = { Text(vm.gymName(workout.gymId)) },
                            leadingIcon = { Icon(Icons.Default.Place, null, Modifier.size(18.dp)) },
                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, "Change gym", Modifier.size(18.dp)) },
                        )
                        // U10: where your partner is, live (from the set they last changed).
                        val partner = members.getOrNull(1)
                        if (active && partner != null) {
                            PartnerPosition(vm.nameOf(partner), workout, sets, partner) { exerciseId ->
                                val i = workout.exercises.indexOfFirst { it.id == exerciseId }
                                if (i >= 0) scope.launch { listState.animateScrollToItem(firstCard + i) }
                            }
                        }
                    }
                }
                if (!active) {
                    item { WorkoutSummary(vm, workout, sets, members) }
                }
                if (!workout.warmUp.isEmpty) {
                    item(key = "warmUp") { CircuitCard("Warm-up", workout.warmUp) }
                }
                if (workout.exercises.isEmpty()) {
                    item { EmptyState("No exercises yet", "Pick from the catalogue below.") }
                }
                itemsIndexed(workout.exercises, key = { _, ex -> ex.id }) { index, ex ->
                    ExerciseCard(
                        vm = vm,
                        workout = workout,
                        exercise = ex,
                        sets = setsByExercise[ex.id].orEmpty(),
                        members = members,
                        memory = memory,
                        canMoveUp = index > 0,
                        canMoveDown = index < workout.exercises.lastIndex,
                        // U2: when you tick the last set of an exercise, bring the next one into view.
                        onAllDone = {
                            if (index < workout.exercises.lastIndex) {
                                scope.launch { listState.animateScrollToItem(firstCard + index + 1) }
                            }
                        },
                    )
                }
                item {
                    OutlinedButton(onClick = { addingExercise = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add exercise")
                    }
                }
                if (!workout.mobility.isEmpty) {
                    item(key = "mobility") { CircuitCard("Mobility", workout.mobility) }
                }
            }
            }
        }
    }

    if (confirmFinish && workout != null) {
        val done = sets.count { it.done }
        AlertDialog(
            onDismissRequest = { confirmFinish = false },
            title = { Text("Finish workout?") },
            text = {
                Text(
                    "$done of ${sets.size} sets ticked off. Only ticked sets count towards your history, " +
                        "charts and remembered weights. This finishes it for both of you."
                )
            },
            confirmButton = { TextButton(onClick = { confirmFinish = false; vm.finishWorkout(workout, sets) }) { Text("Finish") } },
            dismissButton = { TextButton(onClick = { confirmFinish = false }) { Text("Keep going") } },
        )
    }
    if (confirmDelete && workout != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this workout?") },
            text = { Text("It's removed from both of your histories and charts.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.deleteWorkout(workout.id) }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
    if (repeating && workout != null) {
        when {
            workouts.any { it.finishedAt == null } -> {
                AlertDialog(
                    onDismissRequest = { repeating = false },
                    title = { Text("A workout is already running") },
                    text = { Text("Finish or discard it first, then repeat this one.") },
                    confirmButton = { TextButton(onClick = { repeating = false }) { Text("OK") } },
                )
            }
            gyms.size > 1 -> ChooseGymDialog(
                gyms = gyms,
                selectedId = workout.gymId ?: vm.homeGymId(),
                title = "Where are you training?",
                confirmLabel = "Start",
                onDismiss = { repeating = false },
                onChoose = { gymId -> repeating = false; vm.repeatWorkout(workout, gymId) },
            )
            else -> LaunchedEffect(Unit) { repeating = false; vm.repeatWorkout(workout) }
        }
    }
    if (renaming && workout != null) {
        // Current name pre-selected and focused, so you can just type the new one.
        var name by remember { mutableStateOf(TextFieldValue(workout.name, TextRange(0, workout.name.length))) }
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { focus.requestFocus() }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Rename workout") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.copy(text = it.text.take(40)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.focusRequester(focus),
                )
            },
            confirmButton = {
                TextButton(onClick = { renaming = false; vm.renameWorkout(workout, name.text) }, enabled = name.text.isNotBlank()) {
                    Text("Save")
                }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancel") } },
        )
    }
    if (choosingGym && workout != null) {
        ChooseGymDialog(
            gyms = gyms,
            selectedId = workout.gymId ?: vm.homeGymId(),
            title = "Where was this workout?",
            confirmLabel = "Save",
            onDismiss = { choosingGym = false },
            onChoose = { choosingGym = false; vm.changeWorkoutGym(workout, sets, it) },
        )
    }
    if (addingExercise && workout != null) {
        ExercisePicker(
            vm = vm,
            alreadyAdded = workout.exercises.map { exerciseKey(it.name) }.toSet(),
            onDismiss = { addingExercise = false },
            onAdd = { names ->
                addingExercise = false
                vm.addExercises(workout, names)
            },
        )
    }
}

/** Countdown after ticking a set: time left, a progress bar, ±15 s and Skip. */
@Composable
private fun RestBar(endsAt: Long, onAdjust: (Int) -> Unit, onSkip: () -> Unit) {
    var now by remember { mutableStateOf(nowMillis()) }
    val total = remember(endsAt) { (endsAt - nowMillis()).coerceAtLeast(1) }
    LaunchedEffect(endsAt) {
        while (true) { now = nowMillis(); delay(250) }
    }
    val leftMs = (endsAt - now).coerceAtLeast(0)
    val over = leftMs == 0L
    val secs = ((leftMs + 999) / 1000).toInt()
    Surface(
        color = if (over) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 3.dp,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            LinearProgressIndicator(
                progress = { if (over) 1f else 1f - leftMs.toFloat() / total },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Timer, null)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (over) "Rest over. Next set!" else "Rest  ${secs / 60}:${(secs % 60).toString().padStart(2, '0')}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (!over) {
                    TextButton(onClick = { onAdjust(-15) }) { Text("−15s") }
                    TextButton(onClick = { onAdjust(15) }) { Text("+15s") }
                }
                TextButton(onClick = onSkip) { Text(if (over) "OK" else "Skip") }
            }
        }
    }
}

package com.gym.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gym.app.GymLocation
import com.gym.app.GymViewModel
import com.gym.app.Loading
import com.gym.app.LocalAppPlatform
import com.gym.app.Screen
import com.gym.app.WeightMemory
import com.gym.app.Workout
import com.gym.app.WorkoutExercise
import com.gym.app.WorkoutSet
import com.gym.app.exerciseKey
import com.gym.app.formatDateTime
import com.gym.app.formatWeight
import com.gym.app.nowMillis
import com.gym.app.parseWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.gym.app.positionOf
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
            else -> LazyColumn(
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
                                if (i >= 0) scope.launch { listState.animateScrollToItem(1 + i) }
                            }
                        }
                    }
                }
                if (!active) {
                    item { WorkoutSummary(vm, workout, sets, members) }
                }
                if (workout.exercises.isEmpty()) {
                    item { EmptyState("No exercises yet", "Pick from the catalogue below.") }
                }
                val firstCard = if (active) 1 else 2 // lazy index of the first exercise card
                itemsIndexed(workout.exercises, key = { _, ex -> ex.id }) { index, ex ->
                    ExerciseCard(
                        vm = vm,
                        workout = workout,
                        exercise = ex,
                        sets = sets.filter { it.exerciseId == ex.id },
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

@Composable
private fun ExerciseCard(
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
                members.forEachIndexed { i, member ->
                    val accent = if (i == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
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
private fun PartnerPosition(name: String, workout: Workout, sets: List<WorkoutSet>, partner: String, onJump: (String) -> Unit) {
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
private fun CollapsedExerciseCard(
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

@Composable
private fun PersonLabel(name: String, accent: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(accent, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(name, style = MaterialTheme.typography.labelLarge, color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SetRow(set: WorkoutSet, person: String, accent: Color, onChange: (WorkoutSet) -> Unit) {
    // U11: TalkBack reads e.g. "Chris, set 2, weight in kilos, 80".
    val who = "$person, set ${set.order + 1}"
    val haptics = LocalHapticFeedback.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "${set.order + 1}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(14.dp),
        )
        NumberField(
            value = formatWeight(set.weight),
            onValueChange = { onChange(set.copy(weight = parseWeight(it))) },
            placeholder = "kg",
            label = "$who, weight in kilos",
            decimal = true,
            highlight = if (set.done) accent else null,
            modifier = Modifier.weight(1.25f),
        )
        NumberField(
            value = set.reps?.toString().orEmpty(),
            onValueChange = { onChange(set.copy(reps = it.toIntOrNull())) },
            placeholder = "reps",
            label = "$who, reps",
            decimal = false,
            highlight = if (set.done) accent else null,
            modifier = Modifier.weight(1f),
        )
        Box(
            Modifier.size(44.dp)
                .background(if (set.done) accent else MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape)
                .focusProperties { canFocus = false }
                .clickable {
                    haptics.performHapticFeedback(if (set.done) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                    onChange(set.copy(done = !set.done))
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Check,
                contentDescription = if (set.done) "$who done. Tap to untick" else "$who: mark done",
                tint = if (set.done) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/**
 * A compact number box. It keeps its own text while focused so a live update from the other phone
 * never yanks the cursor; when not focused it shows whatever is synced.
 */
@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    label: String,
    decimal: Boolean,
    highlight: Color?,
    modifier: Modifier = Modifier,
) {
    // TextFieldValue so the whole number is selected on focus: typing replaces the pre-filled value
    // (e.g. reps "10" -> type "8") instead of appending to it.
    var field by remember { mutableStateOf(TextFieldValue(value)) }
    val text = field.text
    var focused by remember { mutableStateOf(false) }
    // A tap focuses the field and then places the cursor; keep the select-all from focusing instead.
    var selectAllPending by remember { mutableStateOf(false) }
    LaunchedEffect(value) { if (!focused) field = TextFieldValue(value) }

    val color = MaterialTheme.colorScheme.onSurface
    BasicTextField(
        value = field,
        onValueChange = { new ->
            val cleaned = if (decimal) new.text.filter { it.isDigit() || it == '.' || it == ',' }.take(6)
            else new.text.filter { it.isDigit() }.take(3)
            val separators = cleaned.count { it == '.' || it == ',' }
            when {
                separators > 1 -> Unit
                cleaned == text -> {
                    // Cursor/selection moved only. Right after focusing, keep everything selected.
                    field = if (selectAllPending) {
                        selectAllPending = false
                        new.copy(text = cleaned, selection = TextRange(0, cleaned.length))
                    } else new.copy(text = cleaned)
                }
                else -> {
                    selectAllPending = false
                    field = TextFieldValue(cleaned, TextRange(cleaned.length))
                    onValueChange(cleaned)
                }
            }
        },
        singleLine = true,
        textStyle = TextStyle(color = color, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        // "Next" on the keyboard hops kg -> reps -> next set's kg.
        keyboardOptions = KeyboardOptions(
            keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
            imeAction = ImeAction.Next,
        ),
        modifier = modifier.semantics { contentDescription = label }.onFocusChanged {
            if (it.isFocused && !focused) {
                field = field.copy(selection = TextRange(0, field.text.length))
                selectAllPending = true
            }
            focused = it.isFocused
        },
        decorationBox = { inner ->
            Box(
                Modifier.fillMaxWidth().height(48.dp)
                    .background(
                        highlight?.copy(alpha = 0.18f) ?: MaterialTheme.colorScheme.surfaceContainerHighest,
                        RoundedCornerShape(8.dp),
                    )
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (text.isEmpty()) {
                    Text(placeholder, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                }
                inner()
            }
        },
    )
}

/** "Isometric · gym80 · at Hotel gym", or a faint "Setup" prompt when nothing is set. */
@Composable
private fun SetupLine(isometric: Boolean, brand: String?, otherGym: String?, onClick: () -> Unit) {
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
private fun ExerciseSetupDialog(
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
private fun NoteLine(note: String?, onClick: () -> Unit) {
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
private fun NoteDialog(title: String, subtitle: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
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

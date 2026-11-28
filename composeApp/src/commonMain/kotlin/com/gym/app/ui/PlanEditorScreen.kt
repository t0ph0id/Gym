package com.gym.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gym.app.Circuit
import com.gym.app.Equipment
import com.gym.app.GRIP_SUGGESTIONS
import com.gym.app.GymViewModel
import com.gym.app.Plan
import com.gym.app.PlanExercise
import com.gym.app.PlatformBackHandler
import com.gym.app.exerciseKey
import com.gym.app.newId
import com.gym.app.nowMillis

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanEditorScreen(vm: GymViewModel, planId: String?) {
    val plans by vm.plans.collectAsState()
    val original = remember(planId) { plans.firstOrNull { it.id == planId } }
    var name by remember { mutableStateOf(original?.name.orEmpty()) }
    val exercises = remember {
        mutableStateListOf<PlanExercise>().apply {
            addAll(original?.exercises.orEmpty())
        }
    }
    var warmUp by remember { mutableStateOf(original?.warmUp ?: Circuit()) }
    var mobility by remember { mutableStateOf(original?.mobility ?: Circuit()) }
    /** "warmUp" or "mobility" while picking moves for that section. */
    var pickingCircuit by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    var swappingIndex by remember { mutableStateOf<Int?>(null) }
    var confirmDiscard by remember { mutableStateOf(false) }

    // Unsaved changes? Then Back asks first instead of silently throwing them away.
    val dirty = name != original?.name.orEmpty() || exercises.toList() != original?.exercises.orEmpty() ||
        warmUp != (original?.warmUp ?: Circuit()) || mobility != (original?.mobility ?: Circuit())
    fun leave() { if (dirty) confirmDiscard = true else vm.back() }
    PlatformBackHandler(enabled = dirty) { confirmDiscard = true }

    fun save() {
        vm.savePlan(
            Plan(
                id = original?.id ?: newId(),
                name = name.trim(),
                exercises = exercises.filter { it.name.isNotBlank() }.map { it.copy(name = it.name.trim()) },
                updatedAt = nowMillis(),
                warmUp = warmUp,
                mobility = mobility,
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (original == null) "New plan" else "Edit plan") },
                navigationIcon = { IconButton(onClick = ::leave) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (original != null) {
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Delete plan") }
                    }
                    TextButton(onClick = ::save, enabled = name.isNotBlank()) { Text("Save") }
                },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad).imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text("Plan name") },
                    placeholder = { Text("e.g. Push day") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item(key = "warmUp") {
                CircuitEditor(
                    title = "Warm-up",
                    hint = "Moves to do before the exercises, e.g. bike, leg swings, hip circles. Cardio can have minutes.",
                    circuit = warmUp,
                    onChange = { warmUp = it },
                    onAdd = { pickingCircuit = "warmUp" },
                )
            }
            itemsIndexed(exercises, key = { _, e -> e.id }) { i, ex ->
                ExerciseEditor(
                    ex,
                    brandOptions = vm.gyms.brandOptions(vm.homeGymId()),
                    onChange = { exercises[i] = it },
                    onSwap = { swappingIndex = i },
                    onRemove = { exercises.removeAt(i) },
                    onAnotherGrip = { exercises.add(i + 1, ex.copy(id = newId(), grip = null)) },
                    onMove = { delta -> val j = i + delta; if (j in exercises.indices) exercises.add(j, exercises.removeAt(i)) },
                    canMoveUp = i > 0,
                    canMoveDown = i < exercises.lastIndex,
                )
            }
            item {
                OutlinedButton(
                    onClick = { picking = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add exercises")
                }
            }
            item(key = "mobility") {
                CircuitEditor(
                    title = "Cool-down",
                    hint = "Moves to do after the exercises, e.g. stretches, foam rolling or an easy walk.",
                    circuit = mobility,
                    onChange = { mobility = it },
                    onAdd = { pickingCircuit = "mobility" },
                )
            }
        }
    }

    pickingCircuit?.let { which ->
        val current = if (which == "warmUp") warmUp else mobility
        ExercisePicker(
            vm = vm,
            alreadyAdded = current.exercises.map(::exerciseKey).toSet(),
            mobility = true,
            title = if (which == "warmUp") "Add to warm-up" else "Add to cool-down",
            onDismiss = { pickingCircuit = null },
            onAdd = { names ->
                pickingCircuit = null
                val updated = current.copy(exercises = current.exercises + names)
                if (which == "warmUp") warmUp = updated else mobility = updated
            },
        )
    }

    if (picking) {
        ExercisePicker(
            vm = vm,
            alreadyAdded = exercises.map { exerciseKey(it.name) }.toSet(),
            onDismiss = { picking = false },
            onAdd = { names ->
                picking = false
                exercises.addAll(names.map { PlanExercise(id = newId(), name = it, sets = vm.defaultSets(), reps = vm.defaultReps()) })
            },
        )
    }

    swappingIndex?.let { i ->
        ExercisePicker(
            vm = vm,
            alreadyAdded = exercises.map { exerciseKey(it.name) }.toSet(),
            single = true,
            onDismiss = { swappingIndex = null },
            onAdd = { names ->
                swappingIndex = null
                names.firstOrNull()?.let { n -> if (i in exercises.indices) exercises[i] = exercises[i].copy(name = n) }
            },
        )
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard changes?") },
            text = { Text("Your changes to this plan haven't been saved.") },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; vm.back() }) { Text("Discard") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") } },
        )
    }

    if (confirmDelete && original != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete \"${original.name}\"?") },
            text = { Text("This removes the plan for both of you. Past workouts are kept.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deletePlan(original.id) }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ExerciseEditor(
    ex: PlanExercise,
    brandOptions: List<String>,
    onChange: (PlanExercise) -> Unit,
    onSwap: () -> Unit,
    onRemove: () -> Unit,
    onAnotherGrip: () -> Unit,
    onMove: (Int) -> Unit,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
) {
    var menu by remember { mutableStateOf(false) }
    val catalogEntry = findExercise(ex.name)
    var showDetails by remember { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 12.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ExerciseThumbnail(
                    catalogEntry,
                    48.dp,
                    modifier = Modifier.padding(top = 6.dp, end = 10.dp),
                    onClick = catalogEntry?.let { { showDetails = true } },
                )
                Text(
                    ex.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f).padding(top = 6.dp),
                )
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Exercise options") }
                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Swap exercise") },
                            leadingIcon = { Icon(Icons.Default.SwapHoriz, null) },
                            onClick = { menu = false; onSwap() },
                        )
                        if (canMoveUp) DropdownMenuItem(
                            text = { Text("Move up") },
                            leadingIcon = { Icon(Icons.Default.ArrowUpward, null) },
                            onClick = { menu = false; onMove(-1) },
                        )
                        if (canMoveDown) DropdownMenuItem(
                            text = { Text("Move down") },
                            leadingIcon = { Icon(Icons.Default.ArrowDownward, null) },
                            onClick = { menu = false; onMove(1) },
                        )
                        DropdownMenuItem(
                            text = { Text("Add another grip") },
                            leadingIcon = { Icon(Icons.Default.Add, null) },
                            onClick = { menu = false; onAnotherGrip() },
                        )
                        DropdownMenuItem(
                            text = { Text("Remove") },
                            leadingIcon = { Icon(Icons.Default.Close, null) },
                            onClick = { menu = false; onRemove() },
                        )
                    }
                }
            }
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Stepper("Sets", ex.sets, 1..10) { onChange(ex.copy(sets = it)) }
                Stepper("Reps", ex.reps, 1..50) { onChange(ex.copy(reps = it)) }
            }
            Row(
                Modifier.padding(end = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Type", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FilterChip(!ex.isometric, onClick = { onChange(ex.copy(isometric = false)) }, label = { Text("Normal") })
                FilterChip(ex.isometric, onClick = { onChange(ex.copy(isometric = true)) }, label = { Text("Isometric") })
            }
            // Grip or handle: each one keeps its own weights.
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(end = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Grip", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FilterChip(ex.grip == null, onClick = { onChange(ex.copy(grip = null)) }, label = { Text("Any") })
                (listOfNotNull(ex.grip?.takeIf { g -> GRIP_SUGGESTIONS.none { it.equals(g, true) } }) + GRIP_SUGGESTIONS).forEach { g ->
                    FilterChip(ex.grip.equals(g, true), onClick = { onChange(ex.copy(grip = g)) }, label = { Text(g) })
                }
            }
            // Brand matters for machines and cables (or exercises we don't know).
            val machineLike = catalogEntry == null || catalogEntry.equipment == Equipment.Machine ||
                catalogEntry.equipment == Equipment.Cable
            if (machineLike && brandOptions.isNotEmpty()) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(end = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Machine", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FilterChip(ex.brand == null, onClick = { onChange(ex.copy(brand = null)) }, label = { Text("Any") })
                    brandOptions.forEach { b ->
                        FilterChip(ex.brand == b, onClick = { onChange(ex.copy(brand = b)) }, label = { Text(b) })
                    }
                }
            }
            if (showDetails && catalogEntry != null) {
                ExerciseDetailDialog(catalogEntry, onDismiss = { showDetails = false })
            }
        }
    }
}

@Composable
internal fun Stepper(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        IconButton(onClick = { onChange(value - 1) }, enabled = value > range.first) { Icon(Icons.Default.Remove, "Fewer $label") }
        Text(
            "$value",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 24.dp),
        )
        IconButton(onClick = { onChange(value + 1) }, enabled = value < range.last) { Icon(Icons.Default.Add, "More $label") }
    }
}

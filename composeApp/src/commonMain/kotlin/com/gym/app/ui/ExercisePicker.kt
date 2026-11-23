package com.gym.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.gym.app.Catalog
import com.gym.app.CatalogExercise
import com.gym.app.MobilityCatalog
import com.gym.app.WarmUpKind
import com.gym.app.Equipment
import com.gym.app.GymViewModel
import com.gym.app.LocalAppPlatform
import com.gym.app.Muscle
import com.gym.app.exerciseKey
import com.gym.app.toCatalog
import kotlinx.coroutines.launch

/**
 * Full-screen, searchable exercise catalogue. Pick several, then "Add". Exercises you create are
 * saved for both partners. [alreadyAdded] holds [exerciseKey]s that are shown as already added.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisePicker(
    vm: GymViewModel,
    alreadyAdded: Set<String>,
    onDismiss: () -> Unit,
    onAdd: (List<String>) -> Unit,
    /** Pick exactly one exercise (used to swap one in a plan): tapping a row returns it at once. */
    single: Boolean = false,
    /** For a Warm-up or Mobility section: the warm-up & mobility catalogue, grouped by kind. */
    mobility: Boolean = false,
    title: String = if (single) "Swap exercise" else "Add exercises",
) {
    val custom by vm.ownExercises.list.collectAsState()
    val workouts by vm.workouts.collectAsState()

    val all = remember(custom, mobility) {
        ((if (mobility) MobilityCatalog.exercises else Catalog.exercises) + custom.map { it.toCatalog() })
            .distinctBy { exerciseKey(it.name) }
            .sortedBy { it.name.lowercase() }
    }
    val byKey = remember(all) { all.associateBy { exerciseKey(it.name) } }
    // Most recently used first, from your shared workout history.
    val recent = remember(workouts, byKey) {
        workouts.flatMap { w -> if (mobility) w.warmUp.exercises + w.mobility.exercises else w.exercises.map { it.name } }
            .distinctBy(::exerciseKey)
            .take(8)
            .map { byKey[exerciseKey(it)] ?: CatalogExercise(it, Muscle.Other, Equipment.Other, custom = true) }
    }

    var query by remember { mutableStateOf("") }
    var muscle by remember { mutableStateOf<Muscle?>(null) }
    var equipment by remember { mutableStateOf<Equipment?>(null) }
    var kind by remember { mutableStateOf<WarmUpKind?>(null) }
    var creating by remember { mutableStateOf<String?>(null) }
    var showing by remember { mutableStateOf<CatalogExercise?>(null) }
    val selected = remember { mutableStateListOf<String>() } // names, in the order picked

    fun toggle(name: String) {
        if (single) { onAdd(listOf(name)); return }
        val existing = selected.firstOrNull { exerciseKey(it) == exerciseKey(name) }
        if (existing != null) selected.remove(existing) else selected.add(name)
    }

    fun isSelected(name: String) = selected.any { exerciseKey(it) == exerciseKey(name) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().imePadding()) {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close") } },
                    actions = {
                        if (!single) Button(
                            onClick = { onAdd(selected.toList()) },
                            enabled = selected.isNotEmpty(),
                            modifier = Modifier.padding(end = 8.dp),
                        ) { Text(if (selected.isEmpty()) "Add" else "Add ${selected.size}") }
                    },
                    windowInsets = WindowInsets(0),
                )

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it.take(40) },
                    placeholder = { Text("Search exercises") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Clear search") }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )

                LazyRow(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item { FilterChip(muscle == null, onClick = { muscle = null }, label = { Text("All muscles") }) }
                    items(Muscle.entries.filter { m -> m != Muscle.Other || all.any { it.muscle == m } }) { m ->
                        FilterChip(muscle == m, onClick = { muscle = if (muscle == m) null else m }, label = { Text(m.label) })
                    }
                }
                if (mobility) LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item { FilterChip(kind == null, onClick = { kind = null }, label = { Text("All kinds") }) }
                    items(WarmUpKind.entries) { k ->
                        FilterChip(kind == k, onClick = { kind = if (kind == k) null else k }, label = { Text(k.label) })
                    }
                } else LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item { FilterChip(equipment == null, onClick = { equipment = null }, label = { Text("All equipment") }) }
                    items(Equipment.entries.filter { e -> all.any { it.equipment == e } }) { e ->
                        FilterChip(equipment == e, onClick = { equipment = if (equipment == e) null else e }, label = { Text(e.label) })
                    }
                }

                val q = query.trim()
                val filtered = all.filter {
                    (muscle == null || it.muscle == muscle) &&
                        (equipment == null || it.equipment == equipment) &&
                        (kind == null || it.kind == kind) &&
                        (q.isEmpty() || it.name.contains(q, ignoreCase = true))
                }
                val browsing = q.isEmpty() && muscle == null

                @Composable
                fun Row(ex: CatalogExercise) = ExerciseRow(
                    ex,
                    added = exerciseKey(ex.name) in alreadyAdded,
                    selected = isSelected(ex.name),
                    onToggle = { toggle(ex.name) },
                    onShowDetails = { showing = ex },
                )

                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
                    if (q.isNotEmpty() && exerciseKey(q) !in byKey) {
                        item(key = "create") {
                            ListItem(
                                headlineContent = { Text("Create \"$q\"", color = MaterialTheme.colorScheme.primary) },
                                supportingContent = { Text("Add it to your shared catalogue") },
                                leadingContent = { Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary) },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.clickable { creating = q },
                            )
                        }
                    }
                    if (browsing) {
                        if (recent.isNotEmpty() && equipment == null && kind == null) {
                            item(key = "h-recent") { SectionHeader("Recent") }
                            items(recent, key = { "recent-" + exerciseKey(it.name) }) { Row(it) }
                        }
                        if (mobility) {
                            // Grouped by kind; your own exercises (no kind) at the end.
                            for (k in WarmUpKind.entries) {
                                val group = filtered.filter { it.kind == k }
                                if (group.isEmpty()) continue
                                item(key = "h-${k.name}") { SectionHeader(k.label) }
                                items(group, key = { "all-" + exerciseKey(it.name) }) { Row(it) }
                            }
                            val own = filtered.filter { it.kind == null }
                            if (own.isNotEmpty()) {
                                item(key = "h-own") { SectionHeader("Your own") }
                                items(own, key = { "all-" + exerciseKey(it.name) }) { Row(it) }
                            }
                        } else for (m in Muscle.entries) {
                            val group = filtered.filter { it.muscle == m }
                            if (group.isEmpty()) continue
                            item(key = "h-${m.name}") { SectionHeader(m.label) }
                            items(group, key = { "all-" + exerciseKey(it.name) }) { Row(it) }
                        }
                    } else {
                        items(filtered, key = { "f-" + exerciseKey(it.name) }) { Row(it) }
                    }
                    if (filtered.isEmpty() && q.isEmpty()) {
                        item { EmptyState("Nothing here yet", "Search to create one.") }
                    }
                }
            }
        }
    }

    showing?.let { ExerciseDetailDialog(it, onDismiss = { showing = null }) }

    creating?.let { name ->
        CreateExerciseDialog(
            name = name,
            initialMuscle = muscle ?: Muscle.Other,
            onDismiss = { creating = null },
            onCreate = { m, eq, photo ->
                vm.ownExercises.add(name, m, eq, photo)
                val proper = name.trim().replaceFirstChar { it.uppercase() }
                if (single) onAdd(listOf(proper)) else if (!isSelected(proper)) selected.add(proper)
                creating = null
                query = ""
            },
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun ExerciseRow(
    ex: CatalogExercise,
    added: Boolean,
    selected: Boolean,
    onToggle: () -> Unit,
    onShowDetails: () -> Unit,
) {
    val detail = buildString {
        append(ex.kind?.label ?: ex.equipment.label)
        if (ex.muscle != Muscle.Other && ex.kind != WarmUpKind.Cardio) append(" · ").append(ex.muscle.label)
        if (ex.custom) append(" · Custom")
    }
    ListItem(
        headlineContent = { Text(ex.name) },
        supportingContent = { Text(detail) },
        // Tap the photo for the how-to; tap the row to select.
        leadingContent = { ExerciseThumbnail(ex, 52.dp, onClick = onShowDetails) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // U11: a clear way to the how-to, separate from selecting the row.
                if (ex.imageId != null) {
                    IconButton(onClick = onShowDetails) { Icon(Icons.Outlined.Info, "How to do ${ex.name}") }
                } else if (ex.customId != null) {
                    // Your own exercises: photo, rename and delete live in the same dialog.
                    IconButton(onClick = onShowDetails) { Icon(Icons.Outlined.Edit, "Edit or delete ${ex.name}") }
                }
                if (added) Text("Added", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else Checkbox(checked = selected, onCheckedChange = null)
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = if (added) Modifier else Modifier.clickable(onClick = onToggle),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CreateExerciseDialog(
    name: String,
    initialMuscle: Muscle,
    onDismiss: () -> Unit,
    onCreate: (Muscle, Equipment, ByteArray?) -> Unit,
) {
    var muscle by remember { mutableStateOf(initialMuscle) }
    var equipment by remember { mutableStateOf(Equipment.Other) }
    var photo by remember { mutableStateOf<ByteArray?>(null) }
    val platform = LocalAppPlatform.current
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create \"$name\"") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Muscle group", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Muscle.entries.forEach { m ->
                        FilterChip(muscle == m, onClick = { muscle = m }, label = { Text(m.label) })
                    }
                }
                Text("Equipment", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Equipment.entries.forEach { e ->
                        FilterChip(equipment == e, onClick = { equipment = e }, label = { Text(e.label) })
                    }
                }
                Text("Photo (optional)", style = MaterialTheme.typography.labelLarge)
                photo?.let {
                    AsyncImage(
                        model = it,
                        contentDescription = "Photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(96.dp).clip(RoundedCornerShape(10.dp)),
                    )
                }
                PhotoButtons(
                    onPick = { camera -> scope.launch { platform.pickPhoto(camera)?.let { photo = it } } },
                    onRemove = if (photo != null) ({ photo = null }) else null,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(muscle, equipment, photo) }) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

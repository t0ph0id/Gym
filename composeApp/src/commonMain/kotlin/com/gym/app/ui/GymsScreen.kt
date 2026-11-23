package com.gym.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.gym.app.BRAND_SUGGESTIONS
import com.gym.app.GymLocation
import com.gym.app.GymViewModel
import com.gym.app.newId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GymsScreen(vm: GymViewModel) {
    val gyms by vm.gyms.list.collectAsState()
    val partnership by vm.partnership.collectAsState()
    val homeId = partnership?.homeGymId ?: vm.homeGymId()
    var editing by remember { mutableStateOf<GymLocation?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gyms") },
                navigationIcon = { IconButton(onClick = vm::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = GymLocation(id = newId()) },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Add gym") },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "Weights are remembered separately for each gym and machine brand, because the same exercise can feel very different on another machine.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(gyms, key = { it.id }) { gym ->
                Card(
                    onClick = { editing = gym },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (gym.id == homeId) Icons.Default.Home else Icons.Default.Place,
                            null,
                            tint = if (gym.id == homeId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(gym.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                listOfNotNull(
                                    "Home gym".takeIf { gym.id == homeId },
                                    gym.brands.joinToString(", ").ifEmpty { "No machine brands set" },
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    editing?.let { gym ->
        GymEditorDialog(
            gym = gym,
            isNew = gyms.none { it.id == gym.id },
            isHome = gym.id == homeId,
            onDismiss = { editing = null },
            onSave = { vm.gyms.save(it); editing = null },
            onDelete = { vm.gyms.delete(gym.id); editing = null },
            onMakeHome = { vm.gyms.setHome(gym.id); editing = null },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GymEditorDialog(
    gym: GymLocation,
    isNew: Boolean,
    isHome: Boolean,
    onDismiss: () -> Unit,
    onSave: (GymLocation) -> Unit,
    onDelete: () -> Unit,
    onMakeHome: () -> Unit,
) {
    var name by remember { mutableStateOf(gym.name) }
    val brands = remember { mutableStateListOf<String>().apply { addAll(gym.brands) } }
    var otherBrand by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    val options = (gym.brands + BRAND_SUGGESTIONS + brands).distinctBy { it.lowercase() }

    fun toggle(b: String) {
        val existing = brands.firstOrNull { it.equals(b, ignoreCase = true) }
        if (existing != null) brands.remove(existing) else brands.add(b)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Add gym" else "Edit gym") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(30) },
                    label = { Text("Name") },
                    placeholder = { Text("e.g. Hotel gym") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Machine brands here", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    options.forEach { b ->
                        FilterChip(
                            selected = brands.any { it.equals(b, ignoreCase = true) },
                            onClick = { toggle(b) },
                            label = { Text(b) },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = otherBrand,
                        onValueChange = { otherBrand = it.take(24) },
                        placeholder = { Text("Other brand") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = {
                            val b = otherBrand.trim()
                            if (b.isNotEmpty() && brands.none { it.equals(b, ignoreCase = true) }) brands.add(b)
                            otherBrand = ""
                        },
                        enabled = otherBrand.isNotBlank(),
                    ) { Icon(Icons.Default.Add, "Add brand") }
                }
                if (!isNew) {
                    Row {
                        if (!isHome) {
                            TextButton(onClick = onMakeHome) {
                                Icon(Icons.Default.Home, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Make home gym")
                            }
                            TextButton(onClick = { confirmDelete = true }) {
                                Text("Delete", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(gym.copy(name = name.trim(), brands = brands.toList())) },
                enabled = name.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${gym.name}?") },
            text = { Text("Past workouts stay in your history but will show \"Removed gym\".") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

/** "Where are you training?" Shown before starting a workout when you have more than one gym. */
@Composable
fun ChooseGymDialog(
    gyms: List<GymLocation>,
    selectedId: String,
    title: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onChoose: (String) -> Unit,
) {
    var selected by remember { mutableStateOf(selectedId) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                gyms.forEach { g ->
                    GymRadioRow(g, selected == g.id) { selected = g.id }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onChoose(selected) }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun GymRadioRow(gym: GymLocation, selected: Boolean, onClick: () -> Unit) {
    // The whole row is the tap target, not just the small radio circle.
    Row(
        Modifier.fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.RadioButton(selected = selected, onClick = null)
        Column(Modifier.weight(1f)) {
            Text(gym.name, style = MaterialTheme.typography.bodyLarge)
            if (gym.brands.isNotEmpty()) {
                Text(
                    gym.brands.joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

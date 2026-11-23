package com.gym.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Switch
import com.gym.app.GymViewModel
import com.gym.app.LocalAppPlatform
import com.gym.app.LocalGoogleAuth
import com.gym.app.Screen

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: GymViewModel) {
    val session by vm.session.collectAsState()
    val partnership by vm.partnership.collectAsState()
    val googleAuth = LocalGoogleAuth.current
    val platform = LocalAppPlatform.current
    val currentName = session.profile?.name.orEmpty()
    var name by remember(currentName) { mutableStateOf(currentName) }
    var confirmLeave by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }
    val partnerUid = partnership?.members?.firstOrNull { it != vm.uid }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = vm::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Section("You") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(24) },
                        label = { Text("First name") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = { vm.renameSelf(name) },
                        enabled = name.isNotBlank() && name.trim() != currentName,
                    ) { Text("Save") }
                }
                vm.googleEmail().takeIf { it.isNotEmpty() }?.let {
                    Text(
                        "Signed in with Google as $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            Section("Training partner") {
                if (partnerUid != null) {
                    Text(vm.nameOf(partnerUid), style = MaterialTheme.typography.titleMedium)
                    Text(
                        "You see each other's plans and workouts live.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text("Waiting for your partner", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Ask them to join with this code:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    PairCode(partnership?.code.orEmpty())
                }
                OutlinedButton(onClick = { confirmLeave = true }, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Leave pair")
                }
            }

            Section("New exercises") {
                val p = partnership
                Text(
                    "Sets × reps an exercise starts with when you add it to a plan or workout. Shared by both of you.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                val sets = p?.defaultSets ?: 3
                val reps = p?.defaultReps ?: 10
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Stepper("Sets", sets, 1..10) { vm.setDefaultSetsReps(it, reps) }
                    Stepper("Reps", reps, 1..50) { vm.setDefaultSetsReps(sets, it) }
                }
            }

            Section("Rest timer") {
                val current = session.profile?.restSeconds ?: 90
                Text(
                    "Starts when you tick a set off; the phone buzzes when it's time for the next one.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0, 60, 90, 120, 180).forEach { secs ->
                        FilterChip(
                            selected = current == secs,
                            onClick = { vm.rest.setSeconds(secs) },
                            label = { Text(if (secs == 0) "Off" else if (secs % 60 == 0) "${secs / 60} min" else "${secs / 60}:${(secs % 60).toString().padStart(2, '0')}") },
                        )
                    }
                }
            }

            LocalAppearance.current?.let { appearance ->
                Section("Appearance") {
                    Text(
                        "Just for this phone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { m ->
                            FilterChip(appearance.mode == m, onClick = { appearance.changeMode(m) }, label = { Text(m.label) })
                        }
                    }
                    if (dynamicColorsAvailable) {
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Colours from your wallpaper", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "Instead of Gym's orange (you) and teal (partner).",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(appearance.dynamicColors, onCheckedChange = appearance::changeDynamicColors)
                        }
                    }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                ListItem(
                    headlineContent = { Text("Gyms") },
                    supportingContent = { Text("Your gyms and their machine brands") },
                    leadingContent = { Icon(Icons.Default.Place, null) },
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { vm.navigate(Screen.Gyms) },
                )
            }

            HorizontalDivider()
            OutlinedButton(onClick = { confirmSignOut = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.AutoMirrored.Filled.Logout, null)
                Spacer(Modifier.width(8.dp))
                Text("Sign out")
            }
        }
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Leave this pair?") },
            text = {
                Text(
                    "You'll stop seeing your shared plans and workouts. The current pair code stops working; " +
                        "your partner gets a new one to invite you back."
                )
            },
            confirmButton = { TextButton(onClick = { confirmLeave = false; vm.leavePair() }) { Text("Leave") } },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Cancel") } },
        )
    }
    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Sign out?") },
            text = { Text("Workout data stored on this phone is cleared and the app restarts. Everything is still saved in your account.") },
            confirmButton = { TextButton(onClick = { confirmSignOut = false; vm.signOut(googleAuth, platform) }) { Text("Sign out") } },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            content()
        }
    }
}

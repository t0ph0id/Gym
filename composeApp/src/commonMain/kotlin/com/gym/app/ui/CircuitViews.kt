package com.gym.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Timer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gym.app.CatalogExercise
import com.gym.app.Circuit
import com.gym.app.HealthSession
import com.gym.app.LocalAppPlatform
import com.gym.app.WarmUpKind
import com.gym.app.formatTime
import com.gym.app.nowMillis
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

/** "4 moves · 3 rounds · 10 min" */
fun Circuit.summary(): String =
    (if (exercises.size == 1) "1 move" else "${exercises.size} moves") +
        (if (rounds > 1) " · $rounds rounds" else "") +
        (if (totalMinutes > 0) " · $totalMinutes min" else "")

/** Moves that get a minutes button: cardio, and your own moves (which have no kind). */
private fun timed(entry: CatalogExercise?): Boolean = entry?.kind == null || entry.kind == WarmUpKind.Cardio

/**
 * A Warm-up or Cool-down section, in a plan or a running workout: its moves (in circuit order), how
 * many rounds, and minutes for cardio. Empty, it's just an "Add" button so it stays tidy.
 */
@Composable
fun CircuitEditor(
    title: String,
    hint: String,
    circuit: Circuit,
    onChange: (Circuit) -> Unit,
    /** In a workout: when to look for sessions in Health Connect (e.g. from a Fitbit) for the minutes. */
    healthWindow: LongRange? = null,
    onAdd: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 12.dp, bottom = 4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (circuit.isEmpty) {
                Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Stepper("Rounds", circuit.rounds, 1..10) { onChange(circuit.copy(rounds = it)) }
                circuit.exercises.forEachIndexed { i, name ->
                    CircuitRow(name, circuit.minutesFor(name), healthWindow, onMinutes = { onChange(circuit.withMinutes(name, it)) }) {
                        if (i > 0) IconButton(onClick = {
                            onChange(circuit.copy(exercises = circuit.exercises.toMutableList().apply { add(i - 1, removeAt(i)) }))
                        }) { Icon(Icons.Default.ArrowUpward, "Move $name up") }
                        IconButton(onClick = { onChange(circuit.without(i)) }) { Icon(Icons.Default.Close, "Remove $name") }
                    }
                }
            }
            TextButton(onClick = onAdd) {
                Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (circuit.isEmpty) "Add ${title.lowercase()}" else "Add more")
            }
        }
    }
}

/** A finished workout's Warm-up or Cool-down: the moves, each with its photo, how-to and minutes. */
@Composable
fun CircuitCard(title: String, circuit: Circuit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (circuit.rounds > 1) {
                    Text(
                        "${circuit.rounds} rounds",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            circuit.exercises.forEach { CircuitRow(it, circuit.minutesFor(it), null, onMinutes = null) {} }
        }
    }
}

/**
 * One move: photo (tap for how-to), name and minutes, plus any [actions] on the right. With
 * [onMinutes], cardio moves get a button to set their minutes.
 */
@Composable
private fun CircuitRow(
    name: String,
    minutes: Int?,
    healthWindow: LongRange?,
    onMinutes: ((Int?) -> Unit)?,
    actions: @Composable () -> Unit,
) {
    val entry: CatalogExercise? = findExercise(name)
    var showDetails by remember { mutableStateOf(false) }
    var editingMinutes by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth()
            .then(if (entry != null) Modifier.clickable { showDetails = true } else Modifier)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExerciseThumbnail(entry, 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val info = listOfNotNull(entry?.kind?.label, minutes?.takeIf { onMinutes == null }?.let { "$it min" })
            if (info.isNotEmpty()) {
                Text(info.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (onMinutes != null && (minutes != null || timed(entry))) {
            TextButton(onClick = { editingMinutes = true }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                if (minutes == null) {
                    Icon(Icons.Default.Timer, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("min")
                } else {
                    Text("$minutes min", fontWeight = FontWeight.SemiBold)
                }
            }
        }
        actions()
    }
    if (showDetails && entry != null) ExerciseDetailDialog(entry, onDismiss = { showDetails = false })
    if (editingMinutes && onMinutes != null) {
        MinutesDialog(name, minutes, healthWindow, onDismiss = { editingMinutes = false }) { editingMinutes = false; onMinutes(it) }
    }
}

/**
 * How many minutes on the elliptical (etc.): type it, pick a common one, or (Android) pick a session
 * from Health Connect, e.g. what a Fitbit recorded. Clear removes it.
 */
@Composable
private fun MinutesDialog(name: String, minutes: Int?, healthWindow: LongRange?, onDismiss: () -> Unit, onSave: (Int?) -> Unit) {
    val platform = LocalAppPlatform.current
    val useHealth = healthWindow != null && remember { platform.canReadHealth }
    val scope = rememberCoroutineScope()
    /** null = not loaded yet or not allowed; see [healthAllowed]. */
    var sessions by remember { mutableStateOf<List<HealthSession>?>(null) }
    var healthAllowed by remember { mutableStateOf(true) }
    suspend fun load(ask: Boolean) {
        val found = platform.healthSessions(healthWindow!!.first, minOf(healthWindow.last, nowMillis()), ask)
        healthAllowed = found != null
        sessions = found
    }
    if (useHealth) LaunchedEffect(Unit) { load(ask = false) }
    var text by remember { mutableStateOf(TextFieldValue(minutes?.toString().orEmpty(), TextRange(0, minutes?.toString()?.length ?: 0))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val value = text.text.toIntOrNull()?.takeIf { it in 1..300 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { v -> text = v.copy(text = v.text.filter(Char::isDigit).take(3)) },
                    label = { Text("Minutes") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (value != null) onSave(value) }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(5, 10, 15, 20, 30).forEach { m ->
                        SuggestionChip(onClick = { onSave(m) }, label = { Text("$m") })
                    }
                }
                if (useHealth) {
                    HealthSessions(
                        sessions = sessions,
                        allowed = healthAllowed,
                        since = healthWindow!!.first,
                        onConnect = { scope.launch { load(ask = true) } },
                        onPick = { onSave(it.minutes) },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(value) }, enabled = value != null) { Text("Save") } },
        dismissButton = {
            TextButton(onClick = { if (minutes != null) onSave(null) else onDismiss() }) { Text(if (minutes != null) "Clear" else "Cancel") }
        },
    )
}

/** Sessions from Health Connect to take the minutes from, or a button to allow reading them. */
@Composable
private fun HealthSessions(
    sessions: List<HealthSession>?,
    allowed: Boolean,
    since: Long,
    onConnect: () -> Unit,
    onPick: (HealthSession) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("From Health Connect", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        when {
            !allowed -> OutlinedButton(onClick = onConnect, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.FavoriteBorder, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Use my Fitbit sessions")
            }
            sessions == null -> Text("Loading…", style = MaterialTheme.typography.bodySmall)
            sessions.isEmpty() -> Text(
                "Nothing since ${formatTime(since)}. Your watch's app can take a few minutes to sync.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> sessions.take(4).forEach { session ->
                Card(
                    onClick = { onPick(session) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(session.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOfNotNull("${formatTime(session.start)}–${formatTime(session.end)}", session.source).joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text("${session.minutes} min", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

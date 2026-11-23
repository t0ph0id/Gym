package com.gym.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.heightIn as heightAtMost
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import com.gym.app.Catalog
import com.gym.app.CatalogExercise
import com.gym.app.Equipment
import com.gym.app.LocalAppPlatform
import com.gym.app.Muscle
import com.gym.app.exerciseKey
import kotlinx.coroutines.launch

/** Your own exercises (by [exerciseKey]) and their photos, provided once at the app root. */
class CustomCatalog(
    val byKey: Map<String, CatalogExercise> = emptyMap(),
    val photos: Map<String, ByteArray> = emptyMap(),
    val setPhoto: (customId: String, jpeg: ByteArray?) -> Unit = { _, _ -> },
    val delete: (customId: String) -> Unit = {},
    /** Saves a new name/muscle/equipment; returns a message to show instead if it can't. */
    val edit: (customId: String, name: String, muscle: Muscle, equipment: Equipment) -> String? = { _, _, _, _ -> null },
)

val LocalCustomCatalog = compositionLocalOf { CustomCatalog() }

/** Built-in exercise for [name], or one of your own; null if it's unknown. */
@Composable
fun findExercise(name: String): CatalogExercise? =
    Catalog.find(name) ?: LocalCustomCatalog.current.byKey[exerciseKey(name)]

/** Square photo of the start position; a dumbbell icon when there's no photo (or while offline). */
@Composable
fun ExerciseThumbnail(
    exercise: CatalogExercise?,
    size: Dp,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier.size(size).clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Default.FitnessCenter,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(size / 2),
        )
        val customPhoto = exercise?.customId?.let { LocalCustomCatalog.current.photos[it] }
        (customPhoto ?: exercise?.thumbnailUrl)?.let { model ->
            AsyncImage(
                model = model,
                contentDescription = exercise?.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size),
            )
        }
    }
}

/** Both position photos plus step-by-step instructions. */
@Composable
fun ExerciseDetailDialog(exercise: CatalogExercise, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.heightIn(max = 640.dp).verticalScroll(rememberScrollState()).padding(20.dp)) {
                Text(exercise.name, style = MaterialTheme.typography.headlineSmall)
                Text(
                    buildString {
                        append(exercise.equipment.label)
                        if (exercise.muscle != Muscle.Other) append(" · ").append(exercise.muscle.label)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (exercise.imageUrls.isNotEmpty()) {
                    Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        exercise.imageUrls.forEachIndexed { i, url ->
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                AsyncImage(
                                    model = url,
                                    contentDescription = "${exercise.name}, ${if (i == 0) "start" else "end"} position",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                                        .clip(RoundedCornerShape(12.dp)).background(Color.White),
                                )
                                Text(
                                    if (i == 0) "Start" else "Finish",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }
                }
                if (exercise.instructions.isNotEmpty()) {
                    Text(
                        "How to",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                    )
                    exercise.instructions.forEachIndexed { i, step ->
                        Row(Modifier.padding(vertical = 4.dp)) {
                            Text(
                                "${i + 1}.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.width(24.dp),
                            )
                            Text(step, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                } else if (exercise.custom && exercise.customId != null) {
                    CustomPhotoEditor(exercise.customId)
                    Row {
                        EditCustomExercise(exercise, onSaved = onDismiss)
                        DeleteCustomExercise(exercise, onDeleted = onDismiss)
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End).padding(top = 8.dp)) {
                    Text("Close")
                }
            }
        }
    }
}

/** Photo of one of your own exercises with Take / Choose / Remove; changes sync to your partner. */
@Composable
private fun CustomPhotoEditor(customId: String) {
    val custom = LocalCustomCatalog.current
    val photo = custom.photos[customId]
    val platform = LocalAppPlatform.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    fun pick(camera: Boolean) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                platform.pickPhoto(camera)?.let { custom.setPhoto(customId, it) }
            } finally {
                busy = false
            }
        }
    }
    Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (photo != null) {
            AsyncImage(
                model = photo,
                contentDescription = "Photo",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().heightAtMost(max = 320.dp).clip(RoundedCornerShape(12.dp)),
            )
        } else {
            Text(
                "Your own exercise. Add a photo so you both recognise the machine.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        PhotoButtons(onPick = ::pick, onRemove = if (photo != null) ({ custom.setPhoto(customId, null) }) else null)
    }
}

/** "Take photo" / "Choose photo" (and optionally "Remove"); wraps onto a new line in narrow dialogs. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PhotoButtons(onPick: (fromCamera: Boolean) -> Unit, onRemove: (() -> Unit)? = null) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { onPick(true) }) {
            Icon(Icons.Default.PhotoCamera, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Take")
        }
        OutlinedButton(onClick = { onPick(false) }) {
            Icon(Icons.Default.PhotoLibrary, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Choose")
        }
        if (onRemove != null) TextButton(onClick = onRemove) { Text("Remove") }
    }
}

/** "Edit" for your own exercises: name, muscle group and equipment. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditCustomExercise(exercise: CatalogExercise, onSaved: () -> Unit) {
    val custom = LocalCustomCatalog.current
    var editing by remember { mutableStateOf(false) }
    TextButton(onClick = { editing = true }, modifier = Modifier.padding(top = 8.dp)) { Text("Edit") }
    if (!editing) return
    var name by remember { mutableStateOf(TextFieldValue(exercise.name, TextRange(exercise.name.length))) }
    var muscle by remember { mutableStateOf(exercise.muscle) }
    var equipment by remember { mutableStateOf(exercise.equipment) }
    var error by remember { mutableStateOf<String?>(null) }
    val renaming = exerciseKey(name.text) != exerciseKey(exercise.name)
    AlertDialog(
        onDismissRequest = { editing = false },
        title = { Text("Edit exercise") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.copy(text = it.text.take(40)); error = null },
                    label = { Text("Name") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = {
                        Text(
                            error ?: if (renaming) "Plans, history, weights and charts move to the new name." else "",
                        )
                    },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Muscle group", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Muscle.entries.forEach { m -> FilterChip(muscle == m, onClick = { muscle = m }, label = { Text(m.label) }) }
                }
                Text("Equipment", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Equipment.entries.forEach { e -> FilterChip(equipment == e, onClick = { equipment = e }, label = { Text(e.label) }) }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.text.isNotBlank(),
                onClick = {
                    val id = exercise.customId ?: return@TextButton
                    error = custom.edit(id, name.text, muscle, equipment)
                    if (error == null) { editing = false; onSaved() }
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = { editing = false }) { Text("Cancel") } },
    )
}

/** "Delete exercise" for your own exercises, with a confirmation. */
@Composable
private fun DeleteCustomExercise(exercise: CatalogExercise, onDeleted: () -> Unit) {
    val custom = LocalCustomCatalog.current
    var confirm by remember { mutableStateOf(false) }
    TextButton(onClick = { confirm = true }, modifier = Modifier.padding(top = 8.dp)) {
        Text("Delete exercise", color = MaterialTheme.colorScheme.error)
    }
    if (confirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Delete ${exercise.name}?") },
            text = { Text("It's removed from your shared catalogue, with its photo. Past workouts keep it.") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    exercise.customId?.let(custom.delete)
                    onDeleted()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}

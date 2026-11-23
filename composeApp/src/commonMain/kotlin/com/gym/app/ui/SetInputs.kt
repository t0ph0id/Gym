package com.gym.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gym.app.WorkoutSet
import com.gym.app.formatWeight
import com.gym.app.parseWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/* The weight / reps / tick row for one set, and the number box it uses. */

@Composable
internal fun PersonLabel(name: String, accent: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(accent, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(name, style = MaterialTheme.typography.labelLarge, color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun SetRow(set: WorkoutSet, person: String, accent: Color, onChange: (WorkoutSet) -> Unit) {
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
internal fun NumberField(
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

package com.gym.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import com.gym.app.ChartRange
import com.gym.app.Metric
import com.gym.app.nowMillis
import com.gym.app.value
import com.gym.app.within
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gym.app.GymViewModel
import com.gym.app.ProgressPoint
import com.gym.app.Workout
import com.gym.app.WorkoutSet
import com.gym.app.formatDateTime
import com.gym.app.formatDuration
import com.gym.app.formatWeight

fun setsLabel(n: Int): String = if (n == 1) "1 set" else "$n sets"

/** "1'480 kg" style volume. */
fun formatVolume(kg: Double): String {
    val whole = kg.toLong().toString()
    val grouped = whole.reversed().chunked(3).joinToString("'").reversed() // Swiss style: 1'480
    return "$grouped kg"
}

private fun ProgressPoint.topLabel(): String = when {
    topWeight != null && topReps != null -> "${formatWeight(topWeight)}×$topReps"
    topWeight != null -> "${formatWeight(topWeight)} kg"
    else -> "${topReps ?: 0} reps"
}

/** Home's Progress tab: every exercise you've finished at least once, most recent first. */
@Composable
fun ProgressList(vm: GymViewModel, points: List<ProgressPoint>, onOpen: (String) -> Unit) {
    if (points.isEmpty()) {
        EmptyState("No progress yet", "Finish a workout and each exercise shows up here with a chart.")
        return
    }
    val members = vm.members()
    val byExercise = points.groupBy { it.exerciseKey }.entries.sortedByDescending { e -> e.value.maxOf { it.at } }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        byExercise.forEach { (key, list) ->
            val name = list.last().exercise
            Card(
                onClick = { onOpen(key) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${list.map { it.workoutId }.distinct().size.let { if (it == 1) "1 session" else "$it sessions" }} · last ${formatDateTime(list.maxOf { it.at }).substringBefore(",")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    members.forEachIndexed { i, m ->
                        list.lastOrNull { it.uid == m }?.let { p ->
                            Text(
                                "${vm.nameOf(m)}: ${p.topLabel()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (i == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Chart + history for one exercise, comparing like with like (one gym/machine/type at a time). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProgressScreen(vm: GymViewModel, exerciseKey: String) {
    val all by vm.progress.collectAsState()
    val points = remember(all, exerciseKey) { all.filter { it.exerciseKey == exerciseKey } }
    val members = vm.members()
    val name = points.lastOrNull()?.exercise ?: "Progress"

    // Setups seen for this exercise, most recently used first.
    val setups = remember(points) {
        points.groupBy { it.setupKey }.entries.sortedByDescending { e -> e.value.maxOf { it.at } }.map { it.key }
    }
    var setup by remember(setups) { mutableStateOf(setups.firstOrNull()) }
    val shown = points.filter { it.setupKey == setup }
    var metric by rememberSaveable { mutableStateOf(Metric.TopSet) }
    var range by rememberSaveable { mutableStateOf(ChartRange.All) }
    var selected by remember(setup, metric, range) { mutableStateOf<ProgressPoint?>(null) }
    val now = remember { nowMillis() }

    fun setupLabel(key: String): String {
        val p = points.first { it.setupKey == key }
        return listOfNotNull(vm.gymName(p.gymId), p.brand, "Isometric".takeIf { p.isometric }).joinToString(" · ")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = vm::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (setups.size > 1) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(setups) { key ->
                            FilterChip(setup == key, onClick = { setup = key }, label = { Text(setupLabel(key)) })
                        }
                    }
                }
            } else if (setup != null) {
                item {
                    Text(setupLabel(setup!!), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        // U7: what to plot, and over which period.
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Metric.entries.forEach { m ->
                                FilterChip(metric == m, onClick = { metric = m }, label = { Text(m.label) })
                            }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ChartRange.entries.forEach { r ->
                                FilterChip(range == r, onClick = { range = r }, label = { Text(r.label) })
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        val colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)
                        val inRange = shown.within(range, now)
                        ProgressChart(
                            series = members.mapIndexed { i, m -> colors[i % 2] to inRange.filter { it.uid == m && it.value(metric) != null } },
                            metric = metric,
                            selected = selected,
                            onSelect = { selected = it },
                            modifier = Modifier.fillMaxWidth().height(200.dp),
                        )
                        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            members.forEachIndexed { i, m ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(10.dp).background(colors[i % 2], CircleShape))
                                    Spacer(Modifier.width(6.dp))
                                    Text(vm.nameOf(m), style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                        // The tapped point, or a hint.
                        Text(
                            selected?.let { p ->
                                "${vm.nameOf(p.uid)} · ${formatDateTime(p.at).substringBefore(",")}: " +
                                    "${metric.label.lowercase()} ${formatWeight(p.value(metric))} kg · top ${p.topLabel()} · " +
                                    "${setsLabel(p.sets)} · ${formatVolume(p.volume)}"
                            } ?: "Tap a point for its details.",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (selected != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        if (metric == Metric.OneRepMax) {
                            Text(
                                "Estimated from your top set (Epley formula), so less exact above about 12 reps.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }
            // Session list, newest first.
            val sessions = shown.groupBy { it.workoutId }.entries.sortedByDescending { e -> e.value.maxOf { it.at } }
            items(sessions.map { it.value }, key = { it.first().workoutId }) { session ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(formatDateTime(session.first().at), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        members.forEach { m ->
                            session.firstOrNull { it.uid == m }?.let { p ->
                                Text(
                                    "${vm.nameOf(m)}: top ${p.topLabel()} · ${setsLabel(p.sets)} · ${formatVolume(p.volume)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Line chart: one line per person, x = date, y = [metric]; min/max labelled. Tapping picks the
 * nearest point (it's drawn larger and its details show under the chart).
 */
@Composable
private fun ProgressChart(
    series: List<Pair<Color, List<ProgressPoint>>>,
    metric: Metric,
    selected: ProgressPoint?,
    onSelect: (ProgressPoint?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pts = series.flatMap { it.second }
    if (pts.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text("Nothing to show for this period", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val minV = pts.minOf { it.value(metric)!! }
    val maxV = pts.maxOf { it.value(metric)!! }
    val span = (maxV - minV).takeIf { it > 0 } ?: 1.0
    val minT = pts.minOf { it.at }
    val maxT = pts.maxOf { it.at }
    val grid = MaterialTheme.colorScheme.outlineVariant
    val label: (Double) -> String = { if (metric == Metric.Volume) formatVolume(it).removeSuffix(" kg") else formatWeight(it) }
    Row(modifier) {
        Column(Modifier.width(44.dp).fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Text(label(maxV), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(label(minV), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        var canvasSize by remember { mutableStateOf(IntSize.Zero) }
        val density = LocalDensity.current
        val pad = with(density) { 8.dp.toPx() }
        fun x(t: Long, w: Float) = if (maxT == minT) w / 2 else pad + (w - 2 * pad) * (t - minT).toFloat() / (maxT - minT)
        fun y(v: Double, h: Float) = if (maxV == minV) h / 2 else pad + (h - 2 * pad) * (1f - ((v - minV) / span).toFloat())
        Canvas(
            Modifier.weight(1f).fillMaxSize()
                .onSizeChanged { canvasSize = it }
                .pointerInput(pts, metric) {
                    detectTapGestures { tap ->
                        val w = canvasSize.width.toFloat()
                        val h = canvasSize.height.toFloat()
                        val nearest = pts.minByOrNull { p ->
                            val dx = x(p.at, w) - tap.x
                            val dy = y(p.value(metric)!!, h) - tap.y
                            dx * dx + dy * dy
                        }
                        onSelect(if (nearest == selected) null else nearest)
                    }
                },
        ) {
            drawLine(grid, Offset(0f, pad), Offset(size.width, pad))
            drawLine(grid, Offset(0f, size.height - pad), Offset(size.width, size.height - pad))
            series.forEach { (color, list) ->
                val sorted = list.sortedBy { it.at }
                if (sorted.size > 1) {
                    val path = Path()
                    sorted.forEachIndexed { i, p ->
                        val o = Offset(x(p.at, size.width), y(p.value(metric)!!, size.height))
                        if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
                    }
                    drawPath(path, color, style = Stroke(width = 3.dp.toPx()))
                }
                sorted.forEach { p ->
                    val c = Offset(x(p.at, size.width), y(p.value(metric)!!, size.height))
                    if (p == selected) {
                        drawCircle(color.copy(alpha = 0.25f), radius = 14.dp.toPx(), center = c)
                        drawCircle(color, radius = 7.dp.toPx(), center = c)
                    } else {
                        drawCircle(color, radius = 5.dp.toPx(), center = c)
                    }
                }
            }
        }
    }
}

/** Shown at the top of a finished workout: duration, and per person sets, volume and new bests. */
@Composable
fun WorkoutSummary(vm: GymViewModel, workout: Workout, sets: List<WorkoutSet>, members: List<String>) {
    val end = workout.finishedAt ?: return
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "Summary · ${formatDuration(workout.startedAt, end)}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            members.forEachIndexed { i, m ->
                val theirs = sets.filter { it.uid == m }
                if (theirs.isEmpty()) return@forEachIndexed // e.g. they hadn't joined the pair yet
                val counted = theirs.filter { it.done } // only ticked sets were actually done
                val volume = counted.sumOf { (it.weight ?: 0.0) * (it.reps ?: 0) }
                val accent = if (i == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                Text(
                    "${vm.nameOf(m)}: ${setsLabel(counted.size)} · ${formatVolume(volume)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = accent,
                )
                workout.prs[m]?.takeIf { it.isNotEmpty() }?.let { names ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.EmojiEvents, null, Modifier.size(16.dp), tint = accent)
                        Spacer(Modifier.width(4.dp))
                        Text("New best: " + names.joinToString(", "), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

package com.gym.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import com.gym.app.GymLocation
import com.gym.app.GymViewModel
import com.gym.app.matches
import com.gym.app.nowMillis
import com.gym.app.workoutCalendar
import com.gym.app.Partnership
import com.gym.app.Plan
import com.gym.app.Screen
import com.gym.app.Workout
import com.gym.app.formatDateTime
import com.gym.app.formatDuration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: GymViewModel) {
    val partnership by vm.partnership.collectAsState()
    val plans by vm.plans.collectAsState()
    val workouts by vm.workouts.collectAsState()
    val historyLimit by vm.historyLimit.collectAsState()
    val tab = vm.homeTab
    val recipes by vm.recipes.list.collectAsState()
    val recipesUi = rememberRecipesUi()
    var menuOpen by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var gymFilter by rememberSaveable { mutableStateOf<String?>(null) }
    val gyms by vm.gyms.list.collectAsState()
    val starter = rememberWorkoutStarter(vm)
    fun start(plan: Plan?) = starter.start(plan)
    fun repeat(w: Workout) = starter.repeat(w)

    // Only a workout I'm in blocks starting another; my partner's "Just me" one can be joined instead.
    val active = vm.myActive(workouts)
    val partnerSolo = if (active == null) vm.partnerSolo(workouts) else null
    val history = workouts.filter { it.finishedAt != null }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gym", fontWeight = FontWeight.Bold) },
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "More") }
                        DropdownMenu(menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Gyms") },
                                onClick = { menuOpen = false; vm.navigate(Screen.Gyms) },
                            )
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                onClick = { menuOpen = false; vm.navigate(Screen.Settings) },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (tab == 0) {
                ExtendedFloatingActionButton(
                    onClick = { vm.navigate(Screen.EditPlan(null)) },
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text("New plan") },
                )
            }
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { PartnerCard(partnership, vm) }
            if (active != null) {
                item {
                    ActiveWorkoutCard(active, if (active.isSolo) "Your workout in progress" else "Workout in progress") {
                        vm.navigate(Screen.Workout(active.id))
                    }
                }
            }
            if (partnerSolo != null) {
                item {
                    val who = vm.nameOf(partnerSolo.people.first())
                    ActiveWorkoutCard(partnerSolo, "$who is training", actionLabel = "Join", onAction = {
                        vm.joinWorkout(partnerSolo)
                        vm.navigate(Screen.Workout(partnerSolo.id))
                    }) { vm.navigate(Screen.Workout(partnerSolo.id)) }
                }
            }
            item {
                PrimaryTabRow(selectedTabIndex = tab, containerColor = Color.Transparent) {
                    HomeTab("Plans", tab == 0) { vm.homeTab = 0 }
                    HomeTab("History", tab == 1) { vm.homeTab = 1 }
                    HomeTab("Progress", tab == 2) { vm.homeTab = 2 }
                    HomeTab("Recipes", tab == 3) { vm.homeTab = 3 }
                }
            }
            if (tab == 0) {
                if (active != null) {
                    // U9: say why the start buttons are greyed out.
                    item {
                        Text(
                            "Finish or discard the workout in progress to start another.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                item {
                    OutlinedButton(
                        onClick = { start(null) },
                        enabled = active == null,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Bolt, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Quick workout (no plan)")
                    }
                }
                // U12: do the last workout again, same exercises and setup.
                history.firstOrNull { it.exercises.isNotEmpty() }?.let { last ->
                    item {
                        OutlinedButton(
                            onClick = { repeat(last) },
                            enabled = active == null,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.Replay, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Repeat last: ${last.name} · ${formatDateTime(last.startedAt).substringBefore(",")}",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                if (plans.isEmpty()) {
                    item { EmptyState("No plans yet", "Make a training plan with the exercises you both do.") }
                }
                items(plans, key = { it.id }) { plan ->
                    PlanCard(
                        plan,
                        canStart = active == null,
                        onStart = { start(plan) },
                        onEdit = { vm.navigate(Screen.EditPlan(plan.id)) },
                        onDuplicate = { vm.duplicatePlan(plan) },
                    )
                }
            } else if (tab == 3) {
                recipesTab(vm, recipesUi, recipes)
            } else if (tab == 2) {
                item {
                    // Collected only here, so progress records aren't synced while you're elsewhere.
                    val progress by vm.progress.collectAsState()
                    ProgressList(vm, progress) { key -> vm.navigate(Screen.Progress(key)) }
                }
            } else {
                if (history.isEmpty()) {
                    item { EmptyState("No workouts yet", "Finished workouts show up here.") }
                } else {
                    // U8: how often you've trained lately, then search and filter.
                    item(key = "calendar") { WeekCalendar(history) }
                    item(key = "search") {
                        HistorySearch(query, { query = it }, gyms, gymFilter, { gymFilter = it })
                    }
                }
                val shown = history.filter {
                    (gymFilter == null || (it.gymId ?: vm.homeGymId()) == gymFilter) && it.matches(query, vm.gymName(it.gymId))
                }
                if (history.isNotEmpty() && shown.isEmpty()) {
                    item { EmptyState("No matches", "Nothing in these workouts matches. Older ones can be loaded below.") }
                }
                items(shown, key = { it.id }) { w ->
                    HistoryCard(
                        w,
                        gymName = vm.gymName(w.gymId).takeIf { gyms.size > 1 },
                        soloName = w.people.singleOrNull()?.let { vm.nameOf(it) }?.takeIf { vm.hasPartner() },
                    ) {
                        vm.navigate(Screen.Workout(w.id))
                    }
                }
                if (workouts.size >= historyLimit) {
                    item(key = "older") {
                        TextButton(onClick = vm::showOlderWorkouts, modifier = Modifier.fillMaxWidth()) {
                            Text("Show older workouts")
                        }
                    }
                }
            }
        }
    }

    RecipesDialogs(vm, recipesUi, recipes)

    starter.Dialogs()

}

@Composable
private fun PartnerCard(partnership: Partnership?, vm: GymViewModel) {
    val members = vm.members()
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                Avatar(vm.nameOf(members[0]), MaterialTheme.colorScheme.primary)
                if (members.size > 1) {
                    Box(Modifier.offset(x = 26.dp)) { Avatar(vm.nameOf(members[1]), MaterialTheme.colorScheme.tertiary) }
                }
            }
            Spacer(Modifier.width(if (members.size > 1) 42.dp else 16.dp))
            Column(Modifier.weight(1f)) {
                if (members.size > 1) {
                    Text("${vm.nameOf(members[0])} & ${vm.nameOf(members[1])}", style = MaterialTheme.typography.titleMedium)
                    Text("Training together", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text("Waiting for your partner", style = MaterialTheme.typography.titleMedium)
                    Text("Ask them to join with this code:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    PairCode(partnership?.code.orEmpty())
                }
            }
        }
    }
}

/** The pair code, big, with a Share button to send it to your partner. */
@Composable
fun PairCode(code: String) {
    val platform = com.gym.app.LocalAppPlatform.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            code,
            style = MaterialTheme.typography.headlineSmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        if (code.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = {
                platform.shareText("Join me on Gym: sign in, choose \"Join my partner\" and enter the code $code")
            }) {
                Icon(Icons.Default.Share, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Share")
            }
        }
    }
}

@Composable
fun Avatar(name: String, color: Color, size: Int = 40) {
    Box(
        Modifier.size(size.dp).background(color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.take(1).uppercase(),
            color = MaterialTheme.colorScheme.surface,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun ActiveWorkoutCard(
    workout: Workout,
    label: String,
    actionLabel: String = "Resume",
    onAction: (() -> Unit)? = null,
    onResume: () -> Unit,
) {
    Card(
        onClick = onResume,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(workout.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Button(onClick = onAction ?: onResume) { Text(actionLabel) }
        }
    }
}

@Composable
private fun PlanCard(plan: Plan, canStart: Boolean, onStart: () -> Unit, onEdit: () -> Unit, onDuplicate: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(plan.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = onDuplicate) { Icon(Icons.Default.ContentCopy, "Duplicate plan") }
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Edit plan") }
            }
            Text(
                if (plan.exercises.isEmpty()) "No exercises"
                else "${plan.exercises.size} exercises · " + plan.exercises.joinToString(", ") { if (it.isometric) "${it.name} (isometric)" else it.name },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 8.dp),
            )
            listOfNotNull(
                plan.warmUp.takeUnless { it.isEmpty }?.let { "Warm-up: ${it.summary()}" },
                plan.mobility.takeUnless { it.isEmpty }?.let { "Cool-down: ${it.summary()}" },
            ).takeIf { it.isNotEmpty() }?.let {
                Text(
                    it.joinToString("  ·  "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(top = 2.dp, end = 8.dp),
                )
            }
            Spacer(Modifier.size(12.dp))
            Button(onClick = onStart, enabled = canStart) {
                Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Start workout")
            }
        }
    }
}

@Composable
private fun HistoryCard(workout: Workout, gymName: String?, soloName: String? = null, onClick: () -> Unit) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                formatDateTime(workout.startedAt) + "  ·  " + formatDuration(workout.startedAt, workout.finishedAt ?: workout.startedAt),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(workout.name, style = MaterialTheme.typography.titleMedium)
            if (soloName != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Person, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text("Just $soloName", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (gymName != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Place, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text(gymName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (workout.exercises.isNotEmpty()) {
                Text(
                    workout.exercises.joinToString(", ") { if (it.isometric) "${it.name} (isometric)" else it.name },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** The last 8 weeks at a glance: a square per day (filled = trained), with each week's count. */
@Composable
private fun WeekCalendar(history: List<Workout>) {
    val weeks = remember(history) { workoutCalendar(history.map { it.startedAt }, nowMillis(), weeks = 8) }
    val filled = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.surfaceContainerHighest
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            val thisWeek = weeks.last().total
            Text(
                "This week: ${if (thisWeek == 1) "1 workout" else "$thisWeek workouts"} · last 8 weeks: ${weeks.sumOf { it.total }}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.height(14.dp))
                    }
                }
                weeks.forEach { week ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        week.perDay.forEach { n ->
                            Box(Modifier.size(14.dp).background(if (n > 0) filled else empty, RoundedCornerShape(3.dp)))
                        }
                        Text(
                            "${week.total}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (week.total > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** Search by workout, exercise or gym name, plus a gym filter when you have more than one gym. */
@Composable
private fun HistorySearch(
    query: String,
    onQuery: (String) -> Unit,
    gyms: List<GymLocation>,
    gymFilter: String?,
    onGymFilter: (String?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { onQuery(it.take(40)) },
            placeholder = { Text("Search workouts or exercises") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = if (query.isNotEmpty()) ({ IconButton(onClick = { onQuery("") }) { Icon(Icons.Default.Close, "Clear search") } }) else null,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (gyms.size > 1) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item { FilterChip(gymFilter == null, onClick = { onGymFilter(null) }, label = { Text("All gyms") }) }
                items(gyms, key = { it.id }) { g ->
                    FilterChip(gymFilter == g.id, onClick = { onGymFilter(g.id) }, label = { Text(g.name) })
                }
            }
        }
    }
}

/**
 * A Home tab with less side padding than the default, so all four names stay on one line even with
 * the phone's text size turned up.
 */
@Composable
private fun HomeTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Tab(selected, onClick = onClick, selectedContentColor = MaterialTheme.colorScheme.primary) {
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 14.dp),
        )
    }
}

@Composable
fun EmptyState(title: String, body: String) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

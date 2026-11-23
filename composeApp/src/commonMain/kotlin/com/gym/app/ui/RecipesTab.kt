package com.gym.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.gym.app.GymViewModel
import com.gym.app.RECIPE_TAG_SUGGESTIONS
import com.gym.app.Recipe
import com.gym.app.firstUrl
import com.gym.app.formatDateTime

/** Home's Recipes tab: shared recipe links with search, tag and favourite filters. */
@Composable
fun RecipesTab(vm: GymViewModel) {
    val recipes by vm.recipes.list.collectAsState()
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Recipe?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var tag by rememberSaveable { mutableStateOf<String?>(null) }
    var favouritesOnly by rememberSaveable { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Add a recipe link")
        }
        Text(
            "Tip: on a recipe in Chrome, Instagram or any app, tap Share → Gym.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (recipes.isEmpty()) {
            EmptyState("No recipes yet", "Links you save show up here for both of you.")
        } else {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it.take(40) },
                placeholder = { Text("Search recipes") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = if (query.isNotEmpty()) ({ IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Clear search") } }) else null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            val tagsInUse = recipes.flatMap { it.tags }.distinct().sorted()
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item {
                    FilterChip(
                        favouritesOnly,
                        onClick = { favouritesOnly = !favouritesOnly },
                        label = { Text("Favourites") },
                        leadingIcon = { Icon(Icons.Default.Star, null, Modifier.size(16.dp)) },
                    )
                }
                items(tagsInUse) { t -> FilterChip(tag == t, onClick = { tag = if (tag == t) null else t }, label = { Text(t) }) }
            }
            val q = query.trim().lowercase()
            val shown = recipes.filter { r ->
                (!favouritesOnly || r.favourite) && (tag == null || tag in r.tags) &&
                    (q.isEmpty() || r.title.lowercase().contains(q) || r.site.lowercase().contains(q) ||
                        r.note.lowercase().contains(q) || r.tags.any { it.lowercase().contains(q) })
            }
            if (shown.isEmpty()) EmptyState("No matches", "Try another search or filter.")
            shown.forEach { r ->
                RecipeCard(
                    r,
                    addedBy = vm.nameOf(r.addedBy),
                    onFavourite = { vm.recipes.toggleFavourite(r) },
                    onEdit = { editing = r },
                    onRefresh = { vm.recipes.refreshPreview(r) },
                    onDelete = { vm.recipes.delete(r) },
                )
            }
        }
    }

    if (adding) {
        AddLinkDialog(onDismiss = { adding = false }, onAdd = { text -> adding = false; vm.recipes.addFromText(text) })
    }
    editing?.let { r ->
        val allTags = (RECIPE_TAG_SUGGESTIONS + recipes.flatMap { it.tags }).distinct()
        EditRecipeDialog(r, allTags, onDismiss = { editing = null }, onSave = { editing = null; vm.recipes.save(it) })
    }
}

@Composable
private fun RecipeCard(
    r: Recipe,
    addedBy: String,
    onFavourite: () -> Unit,
    onEdit: () -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
) {
    val uri = LocalUriHandler.current
    var menu by remember { mutableStateOf(false) }
    Card(
        onClick = { runCatching { uri.openUri(r.url) } },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, top = 12.dp, bottom = 12.dp)) {
            Box(
                Modifier.size(76.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Restaurant, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                r.imageUrl?.let {
                    AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(76.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(r.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "${r.site} · $addedBy · ${formatDateTime(r.addedAt).substringBefore(",")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (r.note.isNotBlank()) {
                    Text(r.note, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                if (r.tags.isNotEmpty()) {
                    Text(r.tags.joinToString("  ·  "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = onFavourite) {
                    Icon(
                        if (r.favourite) Icons.Default.Star else Icons.Default.StarBorder,
                        if (r.favourite) "Remove from favourites" else "Add to favourites",
                        tint = if (r.favourite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Recipe options") }
                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Edit note & tags") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { menu = false; onEdit() })
                        DropdownMenuItem(text = { Text("Reload picture & title") }, leadingIcon = { Icon(Icons.Default.Refresh, null) }, onClick = { menu = false; onRefresh() })
                        DropdownMenuItem(text = { Text("Remove") }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { menu = false; onDelete() })
                    }
                }
            }
        }
    }
}

@Composable
private fun AddLinkDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val valid = firstUrl(text) != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a recipe link") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(2000) },
                placeholder = { Text("Paste a link") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (valid) onAdd(text) }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        confirmButton = { TextButton(onClick = { onAdd(text) }, enabled = valid) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditRecipeDialog(recipe: Recipe, allTags: List<String>, onDismiss: () -> Unit, onSave: (Recipe) -> Unit) {
    var title by remember { mutableStateOf(recipe.title) }
    var note by remember { mutableStateOf(recipe.note) }
    var tags by remember { mutableStateOf(recipe.tags) }
    var newTag by remember { mutableStateOf("") }
    val options = (allTags + tags).distinct()
    fun addTag() {
        val t = newTag.trim().replaceFirstChar { it.uppercase() }.take(24)
        if (t.isNotEmpty() && tags.none { it.equals(t, ignoreCase = true) }) tags = tags + t
        newTag = ""
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit recipe") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(140) },
                    label = { Text("Title") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(300) },
                    label = { Text("Note") },
                    placeholder = { Text("e.g. double the protein, less salt") },
                    minLines = 2,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Tags", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    options.forEach { t ->
                        FilterChip(t in tags, onClick = { tags = if (t in tags) tags - t else tags + t }, label = { Text(t) })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newTag,
                        onValueChange = { newTag = it.take(24) },
                        placeholder = { Text("New tag") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { addTag() }),
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = ::addTag, enabled = newTag.isNotBlank()) { Icon(Icons.Default.Add, "Add tag") }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(recipe.copy(title = title.trim().ifEmpty { recipe.title }, note = note.trim(), tags = tags)) },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

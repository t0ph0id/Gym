package com.gym.app.state

import com.gym.app.Recipe
import com.gym.app.UndoNotice
import com.gym.app.fetchLinkPreview
import com.gym.app.firstUrl
import com.gym.app.newId
import com.gym.app.nowMillis
import com.gym.app.siteOf
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Recipe links you share as a pair: added from the share sheet or pasted, previews filled in automatically. */
class Recipes internal constructor(
    private val ctx: VmContext,
    private val uid: () -> String,
    private val offerUndo: (UndoNotice) -> Unit,
) {
    val list: StateFlow<List<Recipe>> = ctx.perPair(emptyList()) { ctx.repo.recipes(it) }

    private val http by lazy { HttpClient() }

    /**
     * Saves the first link in [text] (as shared from Chrome, Instagram, ...) straight away, then
     * fills in its title, site and picture from the page. Says what happened in the snackbar.
     */
    fun addFromText(text: String) {
        val url = firstUrl(text)
        if (url == null) {
            ctx.message.value = "There's no link in that"
            return
        }
        list.value.firstOrNull { sameLink(it.url, url) }?.let {
            ctx.message.value = "Already in Recipes: ${it.title}"
            return
        }
        val site = siteOf(url)
        val recipe = Recipe(id = newId(), url = url, title = site, site = site, addedBy = uid(), addedAt = nowMillis())
        val pid = ctx.currentPid
        ctx.task { ctx.repo.saveRecipe(pid, recipe) }
        ctx.message.value = "Saved to Recipes"
        fillPreview(pid, recipe)
    }

    /** Reads the page again for its title and picture (e.g. after being offline when it was added). */
    fun refreshPreview(recipe: Recipe) = fillPreview(ctx.currentPid, recipe)

    private fun fillPreview(pid: String, recipe: Recipe) {
        ctx.scope.launch(Dispatchers.Default) {
            val preview = fetchLinkPreview(http, recipe.url) ?: return@launch
            ctx.task {
                ctx.repo.setRecipePreview(
                    pid, recipe.id,
                    title = preview.title ?: recipe.title,
                    site = preview.site ?: recipe.site,
                    imageUrl = preview.imageUrl ?: recipe.imageUrl,
                )
            }
        }
    }

    fun save(recipe: Recipe) {
        val pid = ctx.currentPid
        ctx.task { ctx.repo.saveRecipe(pid, recipe) }
    }

    fun toggleFavourite(recipe: Recipe) {
        val pid = ctx.currentPid
        ctx.task { ctx.repo.setRecipeFavourite(pid, recipe.id, !recipe.favourite) }
    }

    fun delete(recipe: Recipe) {
        val pid = ctx.currentPid
        ctx.task { ctx.repo.deleteRecipe(pid, recipe.id) }
        offerUndo(UndoNotice("Recipe removed") { ctx.task { ctx.repo.saveRecipe(pid, recipe) } })
    }

    /** Same page, ignoring http/https, "www." and a trailing slash. */
    private fun sameLink(a: String, b: String): Boolean {
        fun norm(u: String) = u.substringAfter("://").removePrefix("www.").trimEnd('/')
        return norm(a) == norm(b)
    }
}

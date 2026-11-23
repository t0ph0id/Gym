package com.gym.app

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.withTimeoutOrNull

/** What a page says about itself for link previews (the same tags chat apps use). */
data class LinkPreview(val title: String?, val site: String?, val imageUrl: String?)

/** The first web link in shared text, e.g. "Try this! https://example.com/pasta?x=1 so good" -> the URL. */
fun firstUrl(text: String): String? =
    Regex("""https?://[^\s<>"']+""").find(text)?.value?.trimEnd('.', ',', ')', ']', '!', '?', ';', ':')

/** "https://www.bbcgoodfood.com/recipes/x" -> "bbcgoodfood.com" */
fun siteOf(url: String): String =
    url.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore(':').removePrefix("www.")

/**
 * Reads Open Graph / Twitter card / <title> tags from a page's HTML. Relative picture links are made
 * absolute against [pageUrl].
 */
fun parseLinkPreview(html: String, pageUrl: String): LinkPreview {
    val head = html.take(300_000)
    val meta = mutableMapOf<String, String>()
    Regex("""<meta\s[^>]*>""", RegexOption.IGNORE_CASE).findAll(head).forEach { m ->
        val tag = m.value
        val key = attr(tag, "property") ?: attr(tag, "name") ?: return@forEach
        val content = attr(tag, "content") ?: return@forEach
        meta.putIfAbsent(key.lowercase(), decodeEntities(content).trim())
    }
    val titleTag = Regex("""<title[^>]*>(.*?)</title>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        .find(head)?.groupValues?.get(1)?.let { decodeEntities(it).trim() }
    val title = listOf(meta["og:title"], meta["twitter:title"], titleTag).firstOrNull { !it.isNullOrBlank() }
    val image = listOf(meta["og:image"], meta["og:image:url"], meta["twitter:image"]).firstOrNull { !it.isNullOrBlank() }
    return LinkPreview(
        title = title?.replace(Regex("\\s+"), " ")?.take(140),
        site = meta["og:site_name"]?.takeIf { it.isNotBlank() } ?: siteOf(pageUrl),
        imageUrl = image?.let { absolute(it, pageUrl) },
    )
}

private fun attr(tag: String, name: String): String? =
    Regex("""\b$name\s*=\s*(?:"([^"]*)"|'([^']*)')""", RegexOption.IGNORE_CASE).find(tag)
        ?.let { it.groupValues[1].ifEmpty { it.groupValues[2] } }

private fun absolute(link: String, pageUrl: String): String {
    val scheme = pageUrl.substringBefore("://")
    val origin = scheme + "://" + pageUrl.substringAfter("://").substringBefore('/')
    return when {
        link.startsWith("http://") || link.startsWith("https://") -> link
        link.startsWith("//") -> "$scheme:$link"
        link.startsWith("/") -> origin + link
        else -> pageUrl.substringBeforeLast('/') + "/" + link
    }
}

private fun decodeEntities(s: String): String =
    Regex("""&(#x[0-9a-fA-F]+|#[0-9]+|amp|quot|apos|lt|gt|nbsp|#39);""").replace(s) { m ->
        when (val e = m.groupValues[1]) {
            "amp" -> "&"; "quot" -> "\""; "apos", "#39" -> "'"; "lt" -> "<"; "gt" -> ">"; "nbsp" -> " "
            else -> {
                val code = if (e.startsWith("#x")) e.drop(2).toIntOrNull(16) else e.drop(1).toIntOrNull()
                code?.let { c -> if (c in 0..0xFFFF) c.toChar().toString() else m.value } ?: m.value
            }
        }
    }

/** Fetches [url] and reads its preview; null if the page can't be reached within 10 s. */
suspend fun fetchLinkPreview(client: HttpClient, url: String): LinkPreview? = withTimeoutOrNull(10_000) {
    runCatching {
        val response = client.get(url) {
            // Some sites only send preview tags to browsers.
            header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130 Mobile Safari/537.36")
            header("Accept-Language", "en,de;q=0.8")
        }
        if (!response.status.isSuccess()) null else parseLinkPreview(response.bodyAsText(), url)
    }.getOrNull()
}

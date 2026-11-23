package com.gym.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LinkPreviewTest {

    @Test
    fun findsTheLinkInSharedText() {
        assertEquals("https://www.bbcgoodfood.com/recipes/pasta", firstUrl("Try this! https://www.bbcgoodfood.com/recipes/pasta. So good"))
        assertEquals("https://www.instagram.com/reel/abc/?igsh=xyz", firstUrl("https://www.instagram.com/reel/abc/?igsh=xyz"))
        assertNull(firstUrl("no link here"))
    }

    @Test
    fun siteIsTheHostWithoutWww() {
        assertEquals("bbcgoodfood.com", siteOf("https://www.bbcgoodfood.com/recipes/x?y=1"))
        assertEquals("example.com", siteOf("http://example.com:8080/a"))
    }

    @Test
    fun readsOpenGraphTags() {
        val html = """
            <html><head>
            <title>Ignored &amp; fallback</title>
            <meta property="og:title" content="High-protein pasta &amp; peas" />
            <meta content="BBC Good Food" property="og:site_name">
            <meta property='og:image' content='/images/pasta.jpg'>
            </head></html>
        """.trimIndent()
        val p = parseLinkPreview(html, "https://www.bbcgoodfood.com/recipes/pasta")
        assertEquals("High-protein pasta & peas", p.title)
        assertEquals("BBC Good Food", p.site)
        assertEquals("https://www.bbcgoodfood.com/images/pasta.jpg", p.imageUrl)
    }

    @Test
    fun fallsBackToTitleTagAndHost() {
        val p = parseLinkPreview("<title>\n  Overnight oats &#39;n&#x27; berries </title>", "https://www.example.com/oats")
        assertEquals("Overnight oats 'n' berries", p.title)
        assertEquals("example.com", p.site)
        assertNull(p.imageUrl)
    }

    @Test
    fun protocolRelativeAndTwitterImages() {
        val p = parseLinkPreview("""<meta name="twitter:image" content="//cdn.site.com/a.jpg">""", "https://site.com/r")
        assertEquals("https://cdn.site.com/a.jpg", p.imageUrl)
    }
}

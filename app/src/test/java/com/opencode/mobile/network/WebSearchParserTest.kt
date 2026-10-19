package com.opencode.mobile.network

import org.junit.Assert.assertEquals
import org.junit.Test

class WebSearchParserTest {
    @Test
    fun `parses brave result blocks`() {
        val html = """
            <div class="snippet abc" data-pos="0" data-type="web">
              <a href="https://example.com/page">
                <div class="title search-snippet-title line-clamp-1" title="Example result">Example result</div>
              </a>
              <div class="generic-snippet xyz">A <strong>useful</strong> snippet &amp; more.</div>
            </div>
            <div class="snippet abc" data-pos="1" data-type="web">
              <a href="https://second.example">
                <div class="title search-snippet-title line-clamp-1" title="Second result">Second result</div>
              </a>
              <div class="generic-snippet xyz">Another result.</div>
            </div>
        """.trimIndent()

        val results = WebSearchParser.parse(html)

        assertEquals(2, results.size)
        assertEquals("Example result", results[0].title)
        assertEquals("https://example.com/page", results[0].url)
        assertEquals("A useful snippet & more.", results[0].snippet)
        assertEquals("Second result", results[1].title)
    }

    @Test
    fun `returns empty when no result blocks exist`() {
        assertEquals(0, WebSearchParser.parse("<html>no results</html>").size)
    }
}

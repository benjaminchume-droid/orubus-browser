package app.orubus.browser.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UrlResolverTest {
    @Test fun blankInputIsIgnored() {
        assertNull(UrlResolver.resolve("  "))
    }

    @Test fun hostGetsHttps() {
        assertEquals("https://example.com/path", UrlResolver.resolve("example.com/path"))
    }

    @Test fun explicitHttpIsPreserved() {
        assertEquals("http://localhost:8080", UrlResolver.resolve("http://localhost:8080"))
    }

    @Test fun naturalLanguageIsSearched() {
        assertEquals(
            "https://duckduckgo.com/?q=weather+in+Lagos",
            UrlResolver.resolve("weather in Lagos")
        )
    }

    @Test fun dangerousSchemesBecomeSearchQueries() {
        assertEquals(
            "https://duckduckgo.com/?q=javascript%3Aalert%281%29",
            UrlResolver.resolve("javascript:alert(1)")
        )
    }

    @Test fun aboutBlankIsAllowed() {
        assertEquals("about:blank", UrlResolver.resolve("about:blank"))
    }
}

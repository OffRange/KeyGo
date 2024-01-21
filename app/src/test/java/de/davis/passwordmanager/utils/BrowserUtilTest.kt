package de.davis.passwordmanager.utils

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class BrowserUtilTest {

    @Test
    fun testCouldBeUrl() {
        URLS.forEach { (data, result) ->
            assert(BrowserUtil.couldBeUrl(data.first, data.second) == result) {
                "Failed: $data; expected: $result"
            }
        }
    }

    @Test
    fun ensureProtocol() {
        assert(BrowserUtil.ensureProtocol("example.com").startsWith("https://"))
        assert(BrowserUtil.ensureProtocol("http://example.com").equals("http://example.com"))
        assert(BrowserUtil.ensureProtocol("https://example.com").equals("https://example.com"))
    }

    companion object {
        private val URLS = listOf(
            ("Google" to "https://google.com") to true,
            ("Google" to "http://google.com") to true,
            ("fill dev" to "https://fill.dev/some/path") to true,
            ("fill.dev" to "https://fill.dev/some/path") to true,
            ("filldev" to "https://fill.dev/some/path") to true,
            ("Fill Dev" to "https://fill.dev/some/path") to true,
            ("Fill Dev" to "http://fill.dev/some/path") to true,
            ("example" to "https://example.com") to true,
            ("example.com" to "https://example.com") to true,
            ("example.test" to "https://example.com") to false,
            ("example.test" to "http://example.com") to false,
            ("" to "https://example.com") to false,
        )
    }
}
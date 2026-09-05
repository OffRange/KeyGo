package de.davis.keygo.feature.autofill.data.repository

import de.davis.keygo.core.util.Result
import de.davis.keygo.feature.autofill.domain.model.DigitalAssetLinkFailure
import kotlinx.coroutines.test.runTest
import okhttp3.Dns
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetAddress
import java.net.UnknownHostException
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
internal class DigitalAssetLinkRepositoryImplTest {

    @Test
    fun `unresolvable host reports the site as unreachable`() = runTest {
        val repository = DigitalAssetLinkRepositoryImpl(
            OkHttpClient.Builder()
                .dns(object : Dns {
                    override fun lookup(hostname: String): List<InetAddress> =
                        throw UnknownHostException(hostname)
                })
                .build(),
        )

        assertEquals(
            Result.Failure(DigitalAssetLinkFailure.Unreachable),
            repository.isLinked(PACKAGE_NAME, DOMAIN, setOf(SIGNATURE)),
        )
    }

    @Test
    fun `a statement naming the app and signature is linked`() = runTest {
        val site = FakeSite(WELL_KNOWN_URL to Answer(body = statementList()))

        assertEquals(
            Result.Success(true),
            site.repository.isLinked(PACKAGE_NAME, DOMAIN, setOf(SIGNATURE)),
        )
    }

    @Test
    fun `a fingerprint written in another case or without colons still matches`() = runTest {
        val published = listOf(
            "a1:b2:c3:d4",
            "A1B2C3D4",
            "a1b2c3d4",
            " A1:B2:C3:D4 ",
        )

        published.forEach { fingerprint ->
            val site = FakeSite(WELL_KNOWN_URL to Answer(body = statementList(fingerprint)))

            assertEquals(
                Result.Success(true),
                site.repository.isLinked(PACKAGE_NAME, DOMAIN, setOf(SIGNATURE)),
                "expected $fingerprint to match $SIGNATURE",
            )
        }
    }

    @Test
    fun `a site without a statement list is a verdict, not a gap`() = runTest {
        listOf(404, 410).forEach { code ->
            val site = FakeSite(WELL_KNOWN_URL to Answer(code = code, body = "not found"))

            assertEquals(
                Result.Success(false),
                site.repository.isLinked(PACKAGE_NAME, DOMAIN, setOf(SIGNATURE)),
                "expected $code to answer that nothing is published",
            )
        }
    }

    @Test
    fun `a server that fails to answer leaves the question open`() = runTest {
        listOf(500, 503, 429).forEach { code ->
            val site = FakeSite(WELL_KNOWN_URL to Answer(code = code, body = "nope"))

            assertEquals(
                Result.Failure(DigitalAssetLinkFailure.NoVerdict),
                site.repository.isLinked(PACKAGE_NAME, DOMAIN, setOf(SIGNATURE)),
                "expected $code to leave the question open",
            )
        }
    }

    @Test
    fun `a hostile or malformed statement list never escapes as an exception`() = runTest {
        val bodies = listOf(
            """[{"relation": {}}]""",
            """[{"relation": [{}]}]""",
            """[{"target": {"sha256_cert_fingerprints": "not-a-list"}}]""",
            """[{"target": []}]""",
            """{"relation": []}""",
            """[[]]""",
            "null",
            "<html>not json</html>",
            "",
        )

        bodies.forEach { body ->
            val site = FakeSite(WELL_KNOWN_URL to Answer(body = body))

            assertEquals(
                Result.Failure(DigitalAssetLinkFailure.NoVerdict),
                site.repository.isLinked(PACKAGE_NAME, DOMAIN, setOf(SIGNATURE)),
                "expected $body to be reported as unreadable",
            )
        }
    }

    @Test
    fun `a statement list larger than the cap is not parsed`() = runTest {
        val padding = "A".repeat(600 * 1024)
        val oversized =
            statementList().dropLast(2) + ""","pad":"$padding"}]"""
        val site = FakeSite(WELL_KNOWN_URL to Answer(body = oversized))

        assertEquals(
            Result.Failure(DigitalAssetLinkFailure.NoVerdict),
            site.repository.isLinked(PACKAGE_NAME, DOMAIN, setOf(SIGNATURE)),
        )
    }

    @Test
    fun `statements for another app, relation or namespace do not link`() = runTest {
        val bodies = listOf(
            statementList(relation = "delegate_permission/common.handle_all_urls"),
            statementList(namespace = "web"),
            statementList(packageName = "com.other.app"),
            statementList(fingerprint = "FF:FF:FF:FF"),
            "[]",
        )

        bodies.forEach { body ->
            val site = FakeSite(WELL_KNOWN_URL to Answer(body = body))

            assertEquals(
                Result.Success(false),
                site.repository.isLinked(PACKAGE_NAME, DOMAIN, setOf(SIGNATURE)),
                "expected $body not to link",
            )
        }
    }

    @Test
    fun `a plaintext page is still verified over https`() = runTest {
        val site = FakeSite(WELL_KNOWN_URL to Answer(body = statementList()))

        val result = site.repository.isLinked(
            packageName = PACKAGE_NAME,
            domain = "http://example.com/login?redirect=%2Fhome#form",
            signatures = setOf(SIGNATURE),
        )

        assertEquals(Result.Success(true), result)
        assertEquals(listOf(WELL_KNOWN_URL), site.requested)
    }

    @Test
    fun `only the origin of the page reaches the site`() = runTest {
        val origins = mapOf(
            "https://example.com" to WELL_KNOWN_URL,
            "https://example.com/a/b?q=1#f" to WELL_KNOWN_URL,
            "https://EXAMPLE.com" to WELL_KNOWN_URL,
            "http://example.com:80" to WELL_KNOWN_URL,
            "example.com" to WELL_KNOWN_URL,
            // Whatever the page URL carried, no credentials travel to the site.
            "https://user:pw@example.com/a/b" to WELL_KNOWN_URL,
            // A statement list belongs to an origin, so a real port is part of the address.
            "http://example.com:8080/x" to "https://example.com:8080/.well-known/assetlinks.json",
        )

        origins.forEach { (domain, expected) ->
            val site = FakeSite(WELL_KNOWN_URL to Answer(body = statementList()))

            site.repository.isLinked(PACKAGE_NAME, domain, setOf(SIGNATURE))

            assertEquals(listOf(expected), site.requested, "unexpected URL for $domain")
        }
    }

    @Test
    fun `a domain that is not an address is never requested`() = runTest {
        val site = FakeSite(WELL_KNOWN_URL to Answer(body = statementList()))

        assertEquals(
            Result.Failure(DigitalAssetLinkFailure.NoVerdict),
            site.repository.isLinked(PACKAGE_NAME, "https://not a host", setOf(SIGNATURE)),
        )
        assertEquals(emptyList(), site.requested)
    }

    @Test
    fun `a delegated statement list is followed`() = runTest {
        val site = FakeSite(
            WELL_KNOWN_URL to Answer(body = """[{"include": "$INCLUDE_URL"}]"""),
            INCLUDE_URL to Answer(body = statementList()),
        )

        assertEquals(
            Result.Success(true),
            site.repository.isLinked(PACKAGE_NAME, DOMAIN, setOf(SIGNATURE)),
        )
        assertContains(site.requested, INCLUDE_URL)
    }

    @Test
    fun `a delegation we cannot read is not a clean no`() = runTest {
        val site = FakeSite(
            WELL_KNOWN_URL to Answer(body = """[{"include": "$INCLUDE_URL"}]"""),
            INCLUDE_URL to Answer(code = 503, body = "nope"),
        )

        assertEquals(
            Result.Failure(DigitalAssetLinkFailure.NoVerdict),
            site.repository.isLinked(PACKAGE_NAME, DOMAIN, setOf(SIGNATURE)),
        )
    }

    @Test
    fun `a delegation over plaintext is ignored`() = runTest {
        val site = FakeSite(
            WELL_KNOWN_URL to
                    Answer(body = """[{"include": "http://cdn.example.com/statements.json"}]"""),
        )

        assertEquals(
            Result.Success(false),
            site.repository.isLinked(PACKAGE_NAME, DOMAIN, setOf(SIGNATURE)),
        )
        assertEquals(listOf(WELL_KNOWN_URL), site.requested)
    }

    @Test
    fun `an include that is not an absolute https URL is ignored`() = runTest {
        val ignored = listOf(
            "/statements.json",
            "cdn.example.com/statements.json",
            "file:///etc/passwd",
            "not a url",
            "",
        )

        ignored.forEach { include ->
            val site = FakeSite(WELL_KNOWN_URL to Answer(body = includeList(include)))

            assertEquals(
                Result.Success(false),
                site.repository.isLinked(PACKAGE_NAME, DOMAIN, setOf(SIGNATURE)),
                "expected $include not to be followed",
            )
            assertEquals(listOf(WELL_KNOWN_URL), site.requested, "unexpected request for $include")
        }
    }

    @Test
    fun `a cycle between statement lists still ends`() = runTest {
        val site = FakeSite(
            WELL_KNOWN_URL to Answer(body = includeList(INCLUDE_URL)),
            INCLUDE_URL to Answer(body = includeList(WELL_KNOWN_URL)),
        )

        assertEquals(
            Result.Success(false),
            site.repository.isLinked(PACKAGE_NAME, DOMAIN, setOf(SIGNATURE)),
        )
        assertEquals(listOf(WELL_KNOWN_URL, INCLUDE_URL), site.requested)
    }

    @Test
    fun `a list reachable by two paths is read once`() = runTest {
        val left = "https://cdn.example.com/left.json"
        val right = "https://cdn.example.com/right.json"
        val shared = "https://cdn.example.com/shared.json"

        val site = FakeSite(
            WELL_KNOWN_URL to Answer(body = includeList(left, right)),
            left to Answer(body = includeList(shared)),
            right to Answer(body = includeList(shared)),
            shared to Answer(body = "[]"),
        )

        assertEquals(
            Result.Success(false),
            site.repository.isLinked(PACKAGE_NAME, DOMAIN, setOf(SIGNATURE)),
        )
        assertEquals(1, site.requested.count { it == shared })
    }

    @Test
    fun `a delegation chain deeper than the limit is not a clean no`() = runTest {
        val chain = (1..5).map { "https://cdn.example.com/$it.json" }
        val site = FakeSite(
            WELL_KNOWN_URL to Answer(body = includeList(chain.first())),
            *chain.zipWithNext()
                .map { (from, to) -> from to Answer(body = includeList(to)) }
                .toTypedArray(),
            chain.last() to Answer(body = statementList()),
        )

        assertEquals(
            Result.Failure(DigitalAssetLinkFailure.NoVerdict),
            site.repository.isLinked(PACKAGE_NAME, DOMAIN, setOf(SIGNATURE)),
        )
        assertEquals(listOf(WELL_KNOWN_URL) + chain.take(3), site.requested)
    }

    @Test
    fun `a list wider than the fetch budget is not a clean no`() = runTest {
        val fanOut = (1..20).map { "https://cdn.example.com/$it.json" }
        val site = FakeSite(WELL_KNOWN_URL to Answer(body = includeList(*fanOut.toTypedArray())))

        assertEquals(
            Result.Failure(DigitalAssetLinkFailure.NoVerdict),
            site.repository.isLinked(PACKAGE_NAME, DOMAIN, setOf(SIGNATURE)),
        )
        assertEquals(8, site.requested.size)
    }

    @Test
    fun `an unreadable delegation does not hide a valid one`() = runTest {
        val broken = "https://cdn.example.com/broken.json"

        val site = FakeSite(
            WELL_KNOWN_URL to Answer(body = includeList(broken, INCLUDE_URL)),
            broken to Answer(code = 503, body = "nope"),
            INCLUDE_URL to Answer(body = statementList()),
        )

        assertEquals(
            Result.Success(true),
            site.repository.isLinked(PACKAGE_NAME, DOMAIN, setOf(SIGNATURE)),
        )
    }

    private data class Answer(val code: Int = 200, val body: String)

    /**
     * Serves canned bodies per URL and records what was asked for. Anything not configured answers
     * 404, which is what a site that publishes no statement list does.
     */
    private class FakeSite(vararg answers: Pair<String, Answer>) {

        private val answers = answers.toMap()

        val requested: MutableList<String> = mutableListOf()

        val repository = DigitalAssetLinkRepositoryImpl(
            OkHttpClient.Builder()
                .addInterceptor(
                    Interceptor { chain ->
                        val url = chain.request().url.toString()
                        requested += url

                        val answer = this.answers[url] ?: Answer(code = 404, body = "")
                        Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(answer.code)
                            .message("synthetic")
                            .body(answer.body.toResponseBody("application/json".toMediaType()))
                            .build()
                    },
                )
                .build(),
        )
    }

    companion object {

        private const val PACKAGE_NAME = "com.example.app"

        private const val SIGNATURE = "A1:B2:C3:D4"

        private const val DOMAIN = "https://example.com"

        private const val WELL_KNOWN_URL = "https://example.com/.well-known/assetlinks.json"

        private const val INCLUDE_URL = "https://cdn.example.com/statements.json"

        private const val RELATION = "delegate_permission/common.get_login_creds"

        private fun includeList(vararg urls: String) =
            urls.joinToString(prefix = "[", postfix = "]") { """{"include":"$it"}""" }

        private fun statementList(
            fingerprint: String = SIGNATURE,
            relation: String = RELATION,
            namespace: String = "android_app",
            packageName: String = PACKAGE_NAME,
        ) = """
            [{"relation":["$relation"],"target":{"namespace":"$namespace","package_name":"$packageName","sha256_cert_fingerprints":["$fingerprint"]}}]
        """.trimIndent()
    }
}

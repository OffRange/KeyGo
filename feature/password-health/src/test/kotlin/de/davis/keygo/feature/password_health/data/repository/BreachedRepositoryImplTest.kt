package de.davis.keygo.feature.password_health.data.repository

import de.davis.keygo.core.util.assertFailure
import de.davis.keygo.core.util.assertSuccess
import de.davis.keygo.feature.password_health.domain.model.BreachedError
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Robolectric only so the warnings can reach android.util.Log.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BreachedRepositoryImplTest {

    private val api = FakeRangeApi()
    private val repository = BreachedRepositoryImpl(
        OkHttpClient.Builder().addInterceptor(api).build(),
    )

    @Test
    fun asksThePwnedPasswordsRangeEndpointForThePrefix() = runTest {
        api.answer(body = "")

        repository.occurrences(PREFIX, setOf(SUFFIX))

        assertEquals(
            "https://api.pwnedpasswords.com/range/5BAA6",
            api.requests.single().url.toString(),
        )
    }

    @Test
    fun asksForAPaddedUncachedAnswer() = runTest {
        api.answer(body = "")

        repository.occurrences(PREFIX, setOf(SUFFIX))

        val request = api.requests.single()
        assertEquals("true", request.header("Add-Padding"))
        assertEquals("no-store", request.header("Cache-Control"))
        assertEquals("GET", request.method)
    }

    @Test
    fun neverSendsTheSuffixes() = runTest {
        api.answer(body = "")

        repository.occurrences(PREFIX, setOf(SUFFIX))

        assertTrue(SUFFIX !in api.requests.single().url.toString())
    }

    @Test
    fun readsTheCountOfAnAskedSuffix() = runTest {
        api.answer(body = "0018A45C4D1DEF81644B54AB7F969B88D65:1\n$SUFFIX:9545824\n")

        val counts = repository.occurrences(PREFIX, setOf(SUFFIX)).assertSuccess()

        assertEquals(mapOf(SUFFIX to 9_545_824), counts)
    }

    @Test
    fun readsSeveralAskedSuffixesFromOneRange() = runTest {
        api.answer(body = "AAA:1\nBBB:2\nCCC:3\n")

        val counts = repository.occurrences(PREFIX, setOf("AAA", "CCC")).assertSuccess()

        assertEquals(mapOf("AAA" to 1, "CCC" to 3), counts)
    }

    @Test
    fun aSuffixMissingFromTheRangeIsClean() = runTest {
        api.answer(body = "AAA:1\nBBB:2\n")

        val counts = repository.occurrences(PREFIX, setOf(SUFFIX)).assertSuccess()

        assertEquals(emptyMap(), counts)
    }

    @Test
    fun matchesTheSuffixWhateverItsCaseAndKeysItAsAsked() = runTest {
        api.answer(body = "${SUFFIX.lowercase()}:4\n")

        val counts = repository.occurrences(PREFIX, setOf(SUFFIX)).assertSuccess()

        assertEquals(mapOf(SUFFIX to 4), counts)
    }

    @Test
    fun aPaddingLineWithAZeroCountIsClean() = runTest {
        api.answer(body = "$SUFFIX:0\n")

        val counts = repository.occurrences(PREFIX, setOf(SUFFIX)).assertSuccess()

        assertEquals(emptyMap(), counts)
    }

    @Test
    fun aLongerSuffixSharingTheStartIsNotAMatch() = runTest {
        api.answer(body = "AAAB:5\n")

        val counts = repository.occurrences(PREFIX, setOf("AAA")).assertSuccess()

        assertEquals(emptyMap(), counts)
    }

    @Test
    fun malformedLinesAreSkipped() = runTest {
        api.answer(body = "garbage\n:12\nAAA:many\nAAA:\n\nBBB:2\n")

        val counts = repository.occurrences(PREFIX, setOf("AAA", "BBB")).assertSuccess()

        assertEquals(mapOf("BBB" to 2), counts)
    }

    @Test
    fun windowsLineEndingsAndPaddedCountsAreRead() = runTest {
        api.answer(body = "AAA: 7 \r\nBBB:2\r\n")

        val counts = repository.occurrences(PREFIX, setOf("AAA", "BBB")).assertSuccess()

        assertEquals(mapOf("AAA" to 7, "BBB" to 2), counts)
    }

    @Test
    fun aRangeWithoutATrailingNewlineIsReadToTheEnd() = runTest {
        api.answer(body = "AAA:1\nBBB:2")

        val counts = repository.occurrences(PREFIX, setOf("BBB")).assertSuccess()

        assertEquals(mapOf("BBB" to 2), counts)
    }

    @Test
    fun anEmptyRangeIsClean() = runTest {
        api.answer(body = "")

        assertEquals(emptyMap(), repository.occurrences(PREFIX, setOf(SUFFIX)).assertSuccess())
    }

    @Test
    fun aRangeAsLongAsARealOneIsRead() = runTest {
        api.answer(body = paddedRange(lines = 2_000, hit = "$SUFFIX:3"))

        assertEquals(
            mapOf(SUFFIX to 3),
            repository.occurrences(PREFIX, setOf(SUFFIX)).assertSuccess(),
        )
    }

    @Test
    fun aRangeThatNeverEndsIsNotTakenAsClean() = runTest {
        api.answer(body = paddedRange(lines = 20_000, hit = null))

        assertEquals(
            BreachedError.ApiFailed,
            repository.occurrences(PREFIX, setOf(SUFFIX)).assertFailure(),
        )
    }

    @Test
    fun aRangeThatNeverEndsFailsEvenWhenTheHitCameEarly() = runTest {
        api.answer(body = "$SUFFIX:3\n" + paddedRange(lines = 20_000, hit = null))

        assertEquals(
            BreachedError.ApiFailed,
            repository.occurrences(PREFIX, setOf(SUFFIX)).assertFailure(),
        )
    }

    @Test
    fun aServerErrorIsAnApiFailure() = runTest {
        api.answer(code = 500)

        assertEquals(
            BreachedError.ApiFailed,
            repository.occurrences(PREFIX, setOf(SUFFIX)).assertFailure(),
        )
        assertEquals(1, api.requests.size)
    }

    @Test
    fun aClientErrorIsAnApiFailure() = runTest {
        api.answer(code = 400)

        assertEquals(
            BreachedError.ApiFailed,
            repository.occurrences(PREFIX, setOf(SUFFIX)).assertFailure(),
        )
    }

    @Test
    fun aDroppedConnectionIsUnreachable() = runTest {
        api.fail()

        assertEquals(
            BreachedError.Unreachable,
            repository.occurrences(PREFIX, setOf(SUFFIX)).assertFailure(),
        )
        assertEquals(1, api.requests.size)
    }

    @Test
    fun aThrottledLookupIsRetried() = runTest {
        api.answer(code = 429)
        api.answer(body = "$SUFFIX:2\n")

        val counts = repository.occurrences(PREFIX, setOf(SUFFIX)).assertSuccess()

        assertEquals(mapOf(SUFFIX to 2), counts)
        assertEquals(2, api.requests.size)
    }

    @Test
    fun aRetryThatFailsDifferentlyReportsThatFailure() = runTest {
        api.answer(code = 429)
        api.fail()

        assertEquals(
            BreachedError.Unreachable,
            repository.occurrences(PREFIX, setOf(SUFFIX)).assertFailure(),
        )
    }

    @Test
    fun givesUpAfterThreeThrottledAttempts() = runTest {
        repeat(5) { api.answer(code = 429) }

        assertEquals(
            BreachedError.ApiFailed,
            repository.occurrences(PREFIX, setOf(SUFFIX)).assertFailure(),
        )
        assertEquals(3, api.requests.size)
    }

    @Test
    fun waitsAsLongAsRetryAfterAsks() = runTest {
        api.answer(code = 429, retryAfter = "3")
        api.answer(body = "")

        repository.occurrences(PREFIX, setOf(SUFFIX))

        assertEquals(3_000, testScheduler.currentTime)
    }

    @Test
    fun waitsAtLeastASecond() = runTest {
        api.answer(code = 429, retryAfter = "0")
        api.answer(body = "")

        repository.occurrences(PREFIX, setOf(SUFFIX))

        assertEquals(1_000, testScheduler.currentTime)
    }

    @Test
    fun waitsAtMostTenSeconds() = runTest {
        api.answer(code = 429, retryAfter = "3600")
        api.answer(body = "")

        repository.occurrences(PREFIX, setOf(SUFFIX))

        assertEquals(10_000, testScheduler.currentTime)
    }

    @Test
    fun waitsASecondWithoutRetryAfter() = runTest {
        api.answer(code = 429)
        api.answer(body = "")

        repository.occurrences(PREFIX, setOf(SUFFIX))

        assertEquals(1_000, testScheduler.currentTime)
    }

    @Test
    fun waitsASecondWhenRetryAfterIsADate() = runTest {
        api.answer(code = 429, retryAfter = "Wed, 21 Oct 2015 07:28:00 GMT")
        api.answer(body = "")

        repository.occurrences(PREFIX, setOf(SUFFIX))

        assertEquals(1_000, testScheduler.currentTime)
    }

    @Test
    fun noSuffixesAsksNobody() = runTest {
        assertEquals(emptyMap(), repository.occurrences(PREFIX, emptySet()).assertSuccess())
        assertTrue(api.requests.isEmpty())
    }

    @Test
    fun aLowerCasePrefixIsAccepted() = runTest {
        api.answer(body = "")

        repository.occurrences("5baa6", setOf(SUFFIX)).assertSuccess()
    }

    @Test
    fun aPrefixThatIsNotFiveHexCharactersIsRefusedWithoutAsking() = runTest {
        listOf("", "5BAA", "5BAA61", "5BAG6", "5BA 6", "../x", "5BAA6/").forEach { prefix ->
            assertEquals(
                BreachedError.InvalidPrefix,
                repository.occurrences(prefix, setOf(SUFFIX)).assertFailure(),
                "prefix '$prefix'",
            )
        }
        assertTrue(api.requests.isEmpty())
    }

    private fun paddedRange(lines: Int, hit: String?) = buildString {
        repeat(lines) { append("%035X:0\n".format(it)) }
        hit?.let { append(it).append('\n') }
    }

    private class FakeRangeApi : Interceptor {

        val requests: MutableList<Request> = Collections.synchronizedList(mutableListOf())
        private val answers = ArrayDeque<(Request) -> Response>()

        fun answer(code: Int = 200, body: String = "", retryAfter: String? = null) {
            answers += { request ->
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message("")
                    .body(body.toResponseBody())
                    .apply { retryAfter?.let { header("Retry-After", it) } }
                    .build()
            }
        }

        fun fail() {
            answers += { throw IOException("connection reset") }
        }

        override fun intercept(chain: Interceptor.Chain): Response {
            requests += chain.request()
            return synchronized(answers) { answers.removeFirst() }(chain.request())
        }
    }

    private companion object {
        const val PREFIX = "5BAA6"
        const val SUFFIX = "1E4C9B93F3F0682250B6CF8331B7EE68FD8"
    }
}

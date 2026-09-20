package de.davis.keygo.feature.password_health.data.repository

import android.util.Log
import de.davis.keygo.core.util.Result
import de.davis.keygo.feature.password_health.data.repository.BreachedRepositoryImpl.Companion.MAX_RANGE_LINES
import de.davis.keygo.feature.password_health.di.annotation.BreachedQualifier
import de.davis.keygo.feature.password_health.domain.model.BreachedError
import de.davis.keygo.feature.password_health.domain.repository.BreachedRepository
import de.davis.keygo.feature.password_health.domain.repository.BreachedRepository.Companion.PREFIX_LENGTH
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.BufferedSource
import org.koin.core.annotation.Single
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Single
internal class BreachedRepositoryImpl(
    @param:BreachedQualifier
    private val http: OkHttpClient,
) : BreachedRepository {

    override suspend fun occurrences(
        prefix: String,
        suffixes: Set<String>,
    ): Result<Map<String, Int>, BreachedError> {
        if (!prefix.isRangePrefix()) {
            Log.w(TAG, "Refusing to ask for a range that is not $PREFIX_LENGTH hex characters")
            return Result.Failure(BreachedError.InvalidPrefix)
        }

        if (suffixes.isEmpty()) return Result.Success(emptyMap())

        val request = Request.Builder()
            .url(RANGE_URL.toHttpUrl().newBuilder().addPathSegment(prefix).build())
            .header("Add-Padding", "true")
            .header("Cache-Control", "no-store")
            .build()

        return request.fetch(suffixes)
    }

    private suspend fun Request.fetch(
        suffixes: Set<String>,
    ): Result<Map<String, Int>, BreachedError> {
        var attempt = 1

        while (true) {
            when (val outcome = send(suffixes)) {
                is Attempt.Answered -> return Result.Success(outcome.counts)
                is Attempt.Failed -> return Result.Failure(outcome.error)

                is Attempt.Throttled -> {
                    if (attempt == MAX_ATTEMPTS) {
                        Log.w(TAG, "The breach API still throttled us after $attempt attempts")
                        return Result.Failure(BreachedError.ApiFailed)
                    }

                    attempt++
                    delay(outcome.backoff)
                }
            }
        }
    }

    private suspend fun Request.send(suffixes: Set<String>): Attempt =
        suspendCancellableCoroutine { cont ->
            val call = http.newCall(this)
            cont.invokeOnCancellation { call.cancel() }

            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.w(TAG, "Could not reach the breach API", e)
                    cont.resume(Attempt.Failed(BreachedError.Unreachable))
                }

                override fun onResponse(call: Call, response: Response) {
                    cont.resume(response.use { it.read(suffixes) })
                }
            })
        }

    private fun Response.read(suffixes: Set<String>): Attempt {
        if (code == HTTP_TOO_MANY_REQUESTS) return Attempt.Throttled(backoff())

        if (!isSuccessful) {
            Log.w(TAG, "The breach API answered with $code")
            return Attempt.Failed(BreachedError.ApiFailed)
        }

        val counts = body.source().readCounts(suffixes)
        if (counts == null) {
            Log.w(TAG, "The range did not end within $MAX_RANGE_LINES lines")
            return Attempt.Failed(BreachedError.ApiFailed)
        }

        return Attempt.Answered(counts)
    }

    private fun Response.backoff(): Duration = header("Retry-After")
        ?.toLongOrNull()
        ?.seconds
        ?.coerceIn(MIN_BACKOFF, MAX_BACKOFF)
        ?: MIN_BACKOFF

    /** Null when the range ran past [MAX_RANGE_LINES] without ending. */
    private fun BufferedSource.readCounts(suffixes: Set<String>): Map<String, Int>? {
        val counts = mutableMapOf<String, Int>()
        var lines = 0

        while (lines++ < MAX_RANGE_LINES) {
            val line = readUtf8Line() ?: return counts

            val separator = line.indexOf(':')
            if (separator <= 0) continue

            // The matched suffix is reused as the key, so a line we care about is the only thing
            // in the response that turns into a string.
            val suffix = suffixes.firstOrNull {
                it.length == separator && line.startsWith(it, ignoreCase = true)
            } ?: continue

            val count = line.substring(separator + 1).trim().toIntOrNull() ?: continue
            if (count > 0) counts[suffix] = count
        }

        // What we read is not an answer: the suffix we asked about could be past the cap, and
        // reporting that as a clean range is the one mistake this check must not make.
        return null
    }

    private fun String.isRangePrefix() =
        length == PREFIX_LENGTH && all { it.digitToIntOrNull(radix = 16) != null }

    private sealed interface Attempt {
        data class Answered(val counts: Map<String, Int>) : Attempt
        data class Throttled(val backoff: Duration) : Attempt
        data class Failed(val error: BreachedError) : Attempt
    }

    companion object {
        private const val TAG = "BreachedRepositoryImpl"

        private const val RANGE_URL = "https://api.pwnedpasswords.com/range/"
        private const val HTTP_TOO_MANY_REQUESTS = 429

        private const val MAX_ATTEMPTS = 3
        private val MIN_BACKOFF = 1.seconds
        private val MAX_BACKOFF = 10.seconds

        private const val MAX_RANGE_LINES = 8_192
    }
}

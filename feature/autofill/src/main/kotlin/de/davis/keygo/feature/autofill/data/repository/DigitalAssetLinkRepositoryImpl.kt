package de.davis.keygo.feature.autofill.data.repository

import android.util.Log
import de.davis.keygo.core.util.Result
import de.davis.keygo.feature.autofill.domain.model.DigitalAssetLinkFailure
import de.davis.keygo.feature.autofill.domain.repository.DigitalAssetLinkRepository
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.koin.core.annotation.Single
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

@Single
internal class DigitalAssetLinkRepositoryImpl(
    http: OkHttpClient,
) : DigitalAssetLinkRepository {

    /**
     * A statement list only counts at its own well-known location, so an open redirect on the
     * site must not be able to source it from somewhere else, and the user is waiting on a dialog
     * while this runs.
     */
    private val http = http.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    override suspend fun isLinked(
        packageName: String,
        domain: String,
        signatures: Set<String>,
    ): Result<Boolean, DigitalAssetLinkFailure> {
        val url = domain.statementListUrl()
        if (url == null) {
            Log.w(TAG, "Could not build a statement list URL for $domain")
            return Result.Failure(DigitalAssetLinkFailure.NoVerdict)
        }

        val fingerprints = signatures.mapTo(mutableSetOf()) { it.canonicalFingerprint() }

        return search(packageName = packageName, root = url, fingerprints = fingerprints)
    }

    private suspend fun search(
        packageName: String,
        root: HttpUrl,
        fingerprints: Set<String>,
    ): Result<Boolean, DigitalAssetLinkFailure> {
        val deadline = TimeSource.Monotonic.markNow() + LOOKUP_BUDGET

        val queue = ArrayDeque(listOf(Pending(url = root, depth = 0)))
        val visited = mutableSetOf(root)

        var fetches = 0
        var firstFailure: DigitalAssetLinkFailure? = null
        var truncated = false

        while (queue.isNotEmpty()) {
            if (deadline.hasPassedNow()) {
                Log.w(TAG, "Gave up on $root after $LOOKUP_BUDGET, ${queue.size} list(s) unread")
                truncated = true
                break
            }

            val (url, depth) = queue.removeFirst()
            fetches++

            val statements = when (val answer = fetch(url)) {
                is Result.Failure -> {
                    if (firstFailure == null) firstFailure = answer.error
                    continue
                }

                is Result.Success -> answer.success
            }

            if (statements.any { it.links(packageName, fingerprints) })
                return Result.Success(true)

            val includes = statements.mapNotNull { it.include?.includeUrl() }
            if (includes.isEmpty()) continue

            if (depth == MAX_INCLUDE_DEPTH) {
                Log.w(TAG, "Not following the include(s) at $url, depth $depth is the limit")
                truncated = true
                continue
            }

            includes.forEach { include ->
                if (include in visited) return@forEach

                // Counting what is already queued keeps a single very wide list from claiming the
                // whole budget before anything below it has been read.
                if (fetches + queue.size >= MAX_STATEMENT_FETCHES) {
                    Log.w(TAG, "Not queueing $include, $MAX_STATEMENT_FETCHES lists is the limit")
                    truncated = true
                    return@forEach
                }

                visited += include
                queue += Pending(url = include, depth = depth + 1)
            }
        }

        return when {
            firstFailure != null -> Result.Failure(firstFailure)
            truncated -> Result.Failure(DigitalAssetLinkFailure.NoVerdict)
            else -> Result.Success(false)
        }
    }

    private suspend fun fetch(
        url: HttpUrl,
    ): Result<List<AssetLinkStatement>, DigitalAssetLinkFailure> =
        suspendCancellableCoroutine { cont ->
            val call = http.newCall(Request.Builder().url(url).build())
            cont.invokeOnCancellation { call.cancel() }

            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.w(TAG, "Could not reach the statement list at $url", e)
                    cont.resume(Result.Failure(DigitalAssetLinkFailure.Unreachable))
                }

                override fun onResponse(call: Call, response: Response) {
                    cont.resume(response.use { it.readStatements(url) })
                }
            })
        }

    private fun Response.readStatements(
        url: HttpUrl,
    ): Result<List<AssetLinkStatement>, DigitalAssetLinkFailure> {
        if (!isSuccessful) {
            // No statement list means the site delegated nothing to anyone, which is a real answer.
            // Only a server that failed to answer at all leaves the question open.
            if (code in ABSENT_STATEMENT_LIST) return Result.Success(emptyList())

            Log.w(TAG, "Statement list at $url answered with $code")
            return Result.Failure(DigitalAssetLinkFailure.NoVerdict)
        }

        // peekBody caps what a hostile or misconfigured host can make us hold in memory. A list
        // larger than the cap arrives truncated and fails to parse, which is the outcome we want.
        val statements = runCatching {
            json.decodeFromString<List<AssetLinkStatement>>(peekBody(MAX_BODY_BYTES).string())
        }.getOrElse {
            Log.w(TAG, "Statement list at $url is unreadable", it)
            return Result.Failure(DigitalAssetLinkFailure.NoVerdict)
        }

        return Result.Success(statements)
    }

    private fun AssetLinkStatement.links(packageName: String, fingerprints: Set<String>): Boolean {
        if (RELATION !in relation) return false
        if (target.namespace != ANDROID_APP_NAMESPACE) return false
        if (target.packageName != packageName) return false

        return target.fingerprints.any { it.canonicalFingerprint() in fingerprints }
    }

    private fun String.statementListUrl(): HttpUrl? {
        val absolute = if ("://" in this) this else "https://$this"

        return absolute.toHttpUrlOrNull()
            ?.newBuilder()
            ?.scheme("https")
            ?.username("")
            ?.password("")
            ?.encodedPath(WELL_KNOWN_PATH)
            ?.query(null)
            ?.fragment(null)
            ?.build()
    }

    private fun String.includeUrl(): HttpUrl? {
        val url = toHttpUrlOrNull()
        if (url == null) {
            Log.w(TAG, "Ignoring an include that is not an absolute URL")
            return null
        }

        if (!url.isHttps) {
            Log.w(TAG, "Ignoring the plaintext include at $url")
            return null
        }

        return url.newBuilder()
            .username("")
            .password("")
            .fragment(null)
            .build()
    }

    private fun String.canonicalFingerprint() =
        filterNot { it == ':' || it.isWhitespace() }.uppercase()

    companion object {

        private const val TAG = "DigitalAssetLink"

        private const val RELATION = "delegate_permission/common.get_login_creds"
        private const val ANDROID_APP_NAMESPACE = "android_app"
        private const val WELL_KNOWN_PATH = "/.well-known/assetlinks.json"
        private const val MAX_BODY_BYTES = 512L * 1024
        private val ABSENT_STATEMENT_LIST = setOf(404, 410)

        private const val MAX_INCLUDE_DEPTH = 3
        private const val MAX_STATEMENT_FETCHES = 8
        private val LOOKUP_BUDGET = 8.seconds

        private val json = Json { ignoreUnknownKeys = true }
    }
}

private data class Pending(val url: HttpUrl, val depth: Int)

@Serializable
private data class AssetLinkStatement(
    val relation: List<String> = emptyList(),
    val target: AssetLinkTarget = AssetLinkTarget(),
    val include: String? = null,
)

@Serializable
private data class AssetLinkTarget(
    val namespace: String? = null,
    @SerialName("package_name") val packageName: String? = null,
    @SerialName("sha256_cert_fingerprints") val fingerprints: List<String> = emptyList(),
)

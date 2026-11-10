package de.davis.keygo.core.sync.data

import de.davisalessandro.keygo.rust.Header
import de.davisalessandro.keygo.rust.HttpRequest
import de.davisalessandro.keygo.rust.HttpResponse
import de.davisalessandro.keygo.rust.HttpTransport
import de.davisalessandro.keygo.rust.TransportException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Never log [HttpRequest] or [HttpResponse]: their `toString()` prints tokens. */
class OkHttpTransport(client: OkHttpClient) : HttpTransport {

    // No redirects: a DPoP proof is bound to one URL.
    private val client = client.newBuilder()
        .retryOnConnectionFailure(false)
        .followRedirects(false)
        .followSslRedirects(false)
        .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    override suspend fun send(request: HttpRequest): HttpResponse {
        val okRequest = try {
            request.toOkHttp()
        } catch (e: IllegalArgumentException) {
            throw e.toTransportException()
        }

        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(okRequest)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(e.toTransportException())
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        continuation.resume(response.use { it.toRust() })
                    } catch (e: IOException) {
                        continuation.resumeWithException(e.toTransportException())
                    }
                }
            })
        }
    }

    private fun HttpRequest.toOkHttp(): Request {
        val requestBody =
            if (body.isEmpty() && method != "POST") null
            else body.toRequestBody(null)

        return Request.Builder()
            .url(url)
            .apply { headers.forEach { addHeader(it.name, it.value) } }
            .method(method, requestBody)
            .build()
    }

    private fun Response.toRust() = HttpResponse(
        status = code.toUShort(),
        headers = headers.map { (name, value) -> Header(name = name, value = value) },
        body = body.bytes(),
    )

    private fun Exception.toTransportException() =
        TransportException.Failed(reason = message ?: this::class.java.simpleName)

    private companion object {
        const val CALL_TIMEOUT_SECONDS = 30L
    }
}

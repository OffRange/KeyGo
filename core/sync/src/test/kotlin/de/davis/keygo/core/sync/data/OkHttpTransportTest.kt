package de.davis.keygo.core.sync.data

import de.davisalessandro.keygo.rust.Header
import de.davisalessandro.keygo.rust.HttpRequest
import de.davisalessandro.keygo.rust.TransportException
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect
import okhttp3.OkHttpClient
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OkHttpTransportTest {

    private val server = MockWebServer()
    private val transport = OkHttpTransport(OkHttpClient())

    @BeforeTest
    fun setUp() = server.start()

    @AfterTest
    fun tearDown() = server.close()

    private fun request(
        method: String,
        body: ByteArray = byteArrayOf(),
        headers: List<Header> = emptyList(),
    ) = HttpRequest(
        method = method,
        url = server.url("/api").toString(),
        headers = headers,
        body = body,
    )

    @Test
    fun round_trips_method_headers_and_body_bytes() = runTest {
        val payload = byteArrayOf(0, 1, 2, -1, -128, 127)
        server.enqueue(
            MockResponse.Builder()
                .code(201)
                .addHeader("x-reply", "yes")
                .body(okio.Buffer().write(payload))
                .build(),
        )

        val response = transport.send(
            request("POST", payload, listOf(Header(name = "dpop", value = "proof"))),
        )

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("proof", recorded.headers["dpop"])
        assertContentEquals(payload, recorded.body!!.toByteArray())
        assertEquals(201.toUShort(), response.status)
        assertEquals("yes", response.headers.first { it.name.equals("x-reply", true) }.value)
        assertContentEquals(payload, response.body)
    }

    @Test
    fun post_with_empty_body_is_sent() = runTest {
        server.enqueue(MockResponse.Builder().build())

        transport.send(request("POST"))

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals(0, recorded.bodySize)
    }

    @Test
    fun get_sends_no_body() = runTest {
        server.enqueue(MockResponse.Builder().build())

        transport.send(request("GET"))

        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertEquals(0, recorded.bodySize)
    }

    @Test
    fun does_not_retry_after_a_connection_failure() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .onResponseStart(SocketEffect.CloseSocket())
                .build(),
        )

        assertFailsWith<TransportException.Failed> { transport.send(request("GET")) }
        assertEquals(1, server.requestCount)
    }

    @Test
    fun does_not_follow_redirects() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(302)
                .addHeader("Location", server.url("/elsewhere").toString())
                .build(),
        )

        val response = transport.send(request("GET"))

        assertEquals(302.toUShort(), response.status)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun io_failure_throws_transport_exception() = runTest {
        val unreachable = request("GET")
        server.close()

        assertFailsWith<TransportException.Failed> { transport.send(unreachable) }
    }
}

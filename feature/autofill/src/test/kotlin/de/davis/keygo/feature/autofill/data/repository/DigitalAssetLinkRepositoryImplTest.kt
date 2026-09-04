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
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
internal class DigitalAssetLinkRepositoryImplTest {

    @Test
    fun `unresolvable host reports the api as unreachable`() = runTest {
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
            repository.isLinked(PACKAGE_NAME, SIGNATURE, DOMAIN),
        )
    }

    @Test
    fun `linked verdict is reported`() = runTest {
        val repository = repositoryAnswering(body = """{"linked": true}""")

        assertEquals(Result.Success(true), repository.isLinked(PACKAGE_NAME, SIGNATURE, DOMAIN))
    }

    @Test
    fun `unlinked verdict is reported`() = runTest {
        val repository = repositoryAnswering(body = """{"linked": false}""")

        assertEquals(Result.Success(false), repository.isLinked(PACKAGE_NAME, SIGNATURE, DOMAIN))
    }

    @Test
    fun `missing linked flag is treated as unlinked`() = runTest {
        val repository = repositoryAnswering(body = "{}")

        assertEquals(Result.Success(false), repository.isLinked(PACKAGE_NAME, SIGNATURE, DOMAIN))
    }

    @Test
    fun `error status reports no verdict`() = runTest {
        val repository = repositoryAnswering(code = 503, body = "unavailable")

        assertEquals(
            Result.Failure(DigitalAssetLinkFailure.NoVerdict),
            repository.isLinked(PACKAGE_NAME, SIGNATURE, DOMAIN),
        )
    }

    @Test
    fun `unreadable body reports no verdict`() = runTest {
        val repository = repositoryAnswering(body = "<html>not json</html>")

        assertEquals(
            Result.Failure(DigitalAssetLinkFailure.NoVerdict),
            repository.isLinked(PACKAGE_NAME, SIGNATURE, DOMAIN),
        )
    }

    private fun repositoryAnswering(code: Int = 200, body: String) =
        DigitalAssetLinkRepositoryImpl(
            OkHttpClient.Builder()
                .addInterceptor(
                    Interceptor { chain ->
                        Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(code)
                            .message("synthetic")
                            .body(body.toResponseBody("application/json".toMediaType()))
                            .build()
                    },
                )
                .build(),
        )

    companion object {

        private const val PACKAGE_NAME = "com.example"
        private const val SIGNATURE = "AA:BB:CC"
        private const val DOMAIN = "https://example.com"
    }
}

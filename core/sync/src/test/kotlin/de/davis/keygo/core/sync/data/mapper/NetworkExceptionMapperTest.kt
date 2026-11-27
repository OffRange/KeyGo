package de.davis.keygo.core.sync.data.mapper

import de.davisalessandro.keygo.rust.NetworkException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.UnknownServiceException
import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.SSLProtocolException
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals

class NetworkExceptionMapperTest {

    @Test
    fun classifies_each_failure_by_exception_type() {
        val cases = mapOf<Throwable, KClass<out NetworkException>>(
            UnknownHostException("Unable to resolve host") to NetworkException.UnknownHost::class,
            SocketTimeoutException("failed to connect after 10000ms") to NetworkException.Timeout::class,
            InterruptedIOException("timeout") to NetworkException.Timeout::class,
            ConnectException("ECONNREFUSED") to NetworkException.Refused::class,
            NoRouteToHostException("EHOSTUNREACH") to NetworkException.Unreachable::class,
            UnknownServiceException(
                "CLEARTEXT communication to 192.168.178.21 not permitted by network security policy",
            ) to NetworkException.CleartextBlocked::class,
            UnknownServiceException("Unable to find acceptable protocols") to NetworkException.Tls::class,
            SSLPeerUnverifiedException("Hostname not verified") to
                    NetworkException.UntrustedCertificate::class,
            untrustedHandshake() to NetworkException.UntrustedCertificate::class,
            SSLHandshakeException("WRONG_VERSION_NUMBER") to NetworkException.Tls::class,
            SSLProtocolException("Read error") to NetworkException.Tls::class,
            IOException("unexpected end of stream") to NetworkException.Other::class,
            IllegalArgumentException("unexpected url") to NetworkException.Other::class,
        )

        cases.forEach { (error, expected) ->
            assertEquals(expected, error.asRustNetworkException()::class, "for $error")
        }
    }

    /** What Android reports for a self-signed or unknown-CA certificate. */
    private fun untrustedHandshake() = SSLHandshakeException("handshake failed").apply {
        initCause(
            CertificateException(
                CertPathValidatorException("Trust anchor for certification path not found."),
            ),
        )
    }
}

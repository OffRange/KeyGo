package de.davis.keygo.core.sync.data.mapper

import de.davisalessandro.keygo.rust.NetworkException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.UnknownServiceException
import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

internal fun Throwable.asRustNetworkException(): NetworkException = when (this) {
    is UnknownHostException -> NetworkException.UnknownHost()
    // Before InterruptedIOException, which it extends.
    is SocketTimeoutException -> NetworkException.Timeout()
    is ConnectException -> NetworkException.Refused()
    is NoRouteToHostException -> NetworkException.Unreachable()
    is UnknownServiceException -> if (message.orEmpty().contains("CLEARTEXT"))
        NetworkException.CleartextBlocked()
    else NetworkException.Tls()

    is SSLPeerUnverifiedException -> NetworkException.UntrustedCertificate()
    is SSLHandshakeException -> if (hasCertificateCause()) NetworkException.UntrustedCertificate()
    else NetworkException.Tls()

    is SSLException -> NetworkException.Tls()
    // OkHttp's call timeout.
    is InterruptedIOException -> NetworkException.Timeout()
    else -> NetworkException.Other()
}

private fun Throwable.hasCertificateCause() = generateSequence(cause) { it.cause }
    .any { it is CertificateException || it is CertPathValidatorException }

package de.davis.keygo.autofill.data.repository

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import de.davis.keygo.autofill.domain.SignatureInfoProvider
import org.koin.core.annotation.Single
import java.security.MessageDigest

@Single
internal class SignatureInfoProviderImpl(
    private val applicationContext: Context
) : SignatureInfoProvider {

    override fun getSignatureInfo(packageName: String): Set<String> = runCatching {
        val pm = applicationContext.packageManager

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            PackageManager.GET_SIGNING_CERTIFICATES
        else
            @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES

        val info = pm.getPackageInfo(packageName, flags)
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            info.signingInfo?.apkContentsSigners?.toList().orEmpty()
        else
            @Suppress("DEPRECATION") info.signatures?.toList().orEmpty()

        val md = MessageDigest.getInstance("SHA-256")
        signatures.filterNotNull().map { sig ->
            md.digest(sig.toByteArray()).toHexString(format = HEX_FORMAT)
        }.toSet()
    }.fold(
        onSuccess = {
            Log.d(TAG, "Signatures for $packageName: $it")
            it
        },
        onFailure = {
            Log.e(TAG, "Error getting signatures for $packageName", it)
            emptySet()
        }
    )

    companion object {
        private const val TAG = "SignatureInfoProvider"

        private val HEX_FORMAT = HexFormat {
            upperCase = true
            bytes {
                bytesPerGroup = 1
                groupSeparator = ":"
            }
        }
    }
}
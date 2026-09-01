package de.davis.keygo.feature.totp.presentation

import androidx.navigation3.runtime.deeplink.DeepLinkMatcher
import androidx.navigation3.runtime.deeplink.DeepLinkRequest

/**
 * Matches the `otpauth://totp` links the app registers an intent filter for.
 *
 * `UriDeepLinkMatcher` cannot stand in for this. It percent-decodes every value it extracts, and
 * its unnamed query parameter only collects parts that carry no "=", so a real otpauth query is
 * dropped whole. Both matter here, because the link has to reach the parser exactly as it arrived.
 */
object TotpImportDeepLinkMatcher :
    DeepLinkMatcher<TotpImportRedirect, DeepLinkMatcher.MatchResult<TotpImportRedirect>>() {

    const val SCHEME = "otpauth"
    const val HOST = "totp"

    private const val BASE_PATH = "$SCHEME://$HOST"

    override fun matchRequest(
        request: DeepLinkRequest,
    ): MatchResult<TotpImportRedirect>? {
        val uri = request.uri ?: return null
        if (!uri.scheme.equals(SCHEME, ignoreCase = true)) return null
        if (!uri.host.equals(HOST, ignoreCase = true)) return null

        // Both halves are read encoded and glued back together untouched. The parser on the other
        // end percent-decodes each half of the label itself, exactly once. Reading the decoded
        // forms here would decode it a second time, and would already have promoted an escape to
        // a real delimiter on the way: a label written "Acme%23EU" comes back carrying a literal
        // "#", which cuts the query off as a fragment and leaves the import with no secret at all.
        val label = uri.encodedPath?.removePrefix("/")?.takeIf { it.isNotBlank() }
        val query = uri.encodedQuery?.takeIf { it.isNotBlank() }

        return MatchResult(
            TotpImportRedirect(
                uri = if (label != null && query != null) "$BASE_PATH/$label?$query" else null,
            ),
        )
    }
}

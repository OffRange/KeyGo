use fluent_uri::Uri;

/// Compares a proof's `htu` with the request URI as RFC 9449 §4.3 asks: ignoring query and fragment,
/// after RFC 3986 syntax-based (§6.2.2) and scheme-based (§6.2.3) normalization. Either side failing
/// to parse as an absolute URI is a mismatch.
pub(crate) fn matches(htu: &str, request: &str) -> bool {
    match (canonical(htu), canonical(request)) {
        (Some(htu), Some(request)) => htu == request,
        _ => false,
    }
}

/// The RFC 9449 §4.2 form of `uri`: normalized, without query and fragment. `None` unless `uri` is
/// absolute.
pub(crate) fn canonical(uri: &str) -> Option<String> {
    let uri = Uri::parse(uri).ok()?.normalize();
    let mut canonical = format!("{}:", uri.scheme().as_str());

    if let Some(authority) = uri.authority() {
        canonical.push_str("//");
        canonical.push_str(authority.as_str());
    }
    match uri.path().as_str() {
        // RFC 3986 §6.2.3: with an authority, an empty path means "/".
        "" if uri.authority().is_some() => canonical.push('/'),
        path => canonical.push_str(path),
    }

    Some(canonical)
}

#[cfg(test)]
mod tests {
    use super::*;

    const REQUEST: &str = "https://vault.example.org/v1/auth/token";

    #[test]
    fn identical_uris_match() {
        assert!(matches(REQUEST, REQUEST));
    }

    #[test]
    fn ignores_query_and_fragment() {
        assert!(matches(
            "https://vault.example.org/v1/auth/token?a=b#c",
            REQUEST
        ));
        assert!(matches(
            REQUEST,
            "https://vault.example.org/v1/auth/token?x=1"
        ));
    }

    #[test]
    fn scheme_and_host_are_case_insensitive() {
        assert!(matches("HTTPS://Vault.EXAMPLE.org/v1/auth/token", REQUEST));
    }

    #[test]
    fn path_is_case_sensitive() {
        assert!(!matches("https://vault.example.org/v1/Auth/token", REQUEST));
    }

    #[test]
    fn default_port_is_dropped() {
        assert!(matches(
            "https://vault.example.org:443/v1/auth/token",
            REQUEST
        ));
        assert!(!matches(
            "https://vault.example.org:8443/v1/auth/token",
            REQUEST
        ));
    }

    #[test]
    fn unreserved_percent_encoding_is_decoded() {
        assert!(matches(
            "https://vault.example.org/v1/%61uth/token",
            REQUEST
        ));
    }

    #[test]
    fn reserved_percent_encoding_is_kept() {
        assert!(!matches(
            "https://vault.example.org/v1%2Fauth/token",
            REQUEST
        ));
    }

    #[test]
    fn dot_segments_are_removed() {
        assert!(matches(
            "https://vault.example.org/v1/x/../auth/./token",
            REQUEST
        ));
    }

    #[test]
    fn empty_path_equals_root() {
        assert!(matches(
            "https://vault.example.org",
            "https://vault.example.org/"
        ));
    }

    #[test]
    fn other_scheme_or_host_does_not_match() {
        assert!(!matches("http://vault.example.org/v1/auth/token", REQUEST));
        assert!(!matches("https://evil.example.org/v1/auth/token", REQUEST));
    }

    #[test]
    fn unparsable_uri_does_not_match() {
        assert!(!matches("not a uri", REQUEST));
        assert!(!matches("/v1/auth/token", REQUEST));
        assert!(!matches(REQUEST, "not a uri"));
    }
}

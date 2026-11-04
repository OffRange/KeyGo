use url::Url;

#[derive(Debug, thiserror::Error)]
pub enum UrlSanitizeError {
    #[error("empty input")]
    Empty,
    #[error("invalid URL: {0}")]
    InvalidUrl(String),
    #[error("non-http(s) scheme: {0}")]
    NonHttpScheme(String),
}

pub fn sanitize_to_https_url(input: &str) -> Result<Url, UrlSanitizeError> {
    let mut input = input.trim().to_string();
    if input.is_empty() {
        return Err(UrlSanitizeError::Empty);
    }

    if let Some(idx) = input.find("://") {
        let scheme = &input[..idx].to_ascii_lowercase();
        if scheme != "http" && scheme != "https" {
            return Err(UrlSanitizeError::NonHttpScheme(scheme.to_string()));
        }
    } else {
        // Colon-only schemes; "example.com:8080" is not one.
        if let Some(colon) = input.find(':') {
            let maybe_scheme = input[..colon].to_ascii_lowercase();
            if matches!(
                maybe_scheme.as_str(),
                "mailto" | "data" | "javascript" | "file" | "blob" | "about"
            ) {
                return Err(UrlSanitizeError::NonHttpScheme(maybe_scheme));
            }
        }

        if input.starts_with("//") {
            input = format!("https:{input}");
        } else if !input.starts_with("http://") && !input.starts_with("https://") {
            input = format!("https://{input}");
        }
    }

    let url = Url::parse(&input).map_err(|e| UrlSanitizeError::InvalidUrl(e.to_string()))?;

    match url.scheme() {
        "http" | "https" => Ok(url),
        other => Err(UrlSanitizeError::NonHttpScheme(other.to_string())),
    }
}

#[cfg(test)]
mod tests {
    use crate::url::sanitize_to_https_url;
    use std::collections::HashMap;

    #[test]
    fn test_sanitize_to_https_url() {
        let test_map = HashMap::from([
            ("", false),
            ("   ", false),
            ("ftp://example.com", false),
            ("http://example.com", true),
            ("https://example.com", true),
            ("wss://example.com", false),
            ("mailto:foo@bar", false),
            ("data:text/plain,hi", false),
            ("javascript:alert(1)", false),
            ("file:///etc/hosts", false),
            ("file://etc/hosts", false),
            ("//example.com:3000/a", true),
            ("HTTPS://Example.Com", true),
            ("example.com", true),
            ("example.com:3000", true),
        ]);

        for (url, valid) in test_map {
            assert_eq!(sanitize_to_https_url(url).is_ok(), valid);
        }
    }
}

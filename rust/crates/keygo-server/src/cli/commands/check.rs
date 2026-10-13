use crate::config::{Config, DatabaseConfig, Environment};
use crate::secrets::{Secret, SecretStore};
use anstream::println;
use anstyle::{AnsiColor, Style};
use std::fs;
use std::os::unix::fs::PermissionsExt;
use std::path::Path;
use url::Url;

pub fn run(config: &Config) -> anyhow::Result<()> {
    let sections = [check_config(config), check_secrets(&config.secrets_dir)];
    print_report(&sections);

    if sections.iter().any(|s| s.count(Status::Fail) > 0) {
        std::process::exit(1);
    }
    Ok(())
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
enum Status {
    Pass,
    Warn,
    Fail,
}

impl Status {
    fn symbol(self) -> &'static str {
        match self {
            Status::Pass => "✓",
            Status::Warn => "!",
            Status::Fail => "✗",
        }
    }

    fn style(self) -> Style {
        let color = match self {
            Status::Pass => AnsiColor::Green,
            Status::Warn => AnsiColor::Yellow,
            Status::Fail => AnsiColor::Red,
        };
        Style::new().fg_color(Some(color.into())).bold()
    }
}

#[derive(Debug)]
struct Check {
    status: Status,
    label: String,
    detail: String,
}

impl Check {
    fn new(status: Status, label: impl Into<String>, detail: impl Into<String>) -> Self {
        Self {
            status,
            label: label.into(),
            detail: detail.into(),
        }
    }
}

struct Section {
    title: String,
    checks: Vec<Check>,
}

impl Section {
    fn count(&self, status: Status) -> usize {
        self.checks.iter().filter(|c| c.status == status).count()
    }
}

fn check_config(config: &Config) -> Section {
    Section {
        title: "Config".into(),
        checks: vec![
            check_environment(config.environment),
            check_public_url(&config.public_url, config.environment),
            check_signups(config.allow_signups, config.environment),
            check_database(&config.database),
        ],
    }
}

fn check_environment(environment: Environment) -> Check {
    let status = match environment {
        Environment::Production => Status::Pass,
        Environment::Development => Status::Warn,
    };

    Check::new(status, "environment", environment.name())
}

fn check_public_url(raw: &str, environment: Environment) -> Check {
    let label = "public url";
    let url = match Url::parse(raw) {
        Ok(url) => url,
        Err(e) => return Check::new(Status::Fail, label, format!("{raw:?} is not a URL: {e}")),
    };

    if !matches!(url.scheme(), "http" | "https") {
        return Check::new(
            Status::Fail,
            label,
            format!("{raw} must use http or https, not {}", url.scheme()),
        );
    }

    if url.host().is_none() {
        return Check::new(Status::Fail, label, format!("{raw} has no host"));
    }

    if environment == Environment::Production && url.scheme() != "https" {
        return Check::new(
            Status::Fail,
            label,
            format!("{raw} must use https in production"),
        );
    }

    Check::new(Status::Pass, label, raw)
}

fn check_signups(allow_signups: bool, environment: Environment) -> Check {
    let label = "signups";
    match (allow_signups, environment) {
        (true, Environment::Production) => {
            Check::new(Status::Warn, label, "enabled, anyone can create an account")
        }
        (true, _) => Check::new(Status::Pass, label, "enabled"),
        (false, _) => Check::new(Status::Pass, label, "disabled"),
    }
}

fn check_database(db: &DatabaseConfig) -> Check {
    let label = "database";
    let missing: Vec<_> = [
        ("host", &db.host),
        ("name", &db.name),
        ("username", &db.username),
    ]
    .into_iter()
    .filter(|(_, value)| value.trim().is_empty())
    .map(|(field, _)| field)
    .collect();

    if !missing.is_empty() {
        Check::new(Status::Fail, label, format!("empty {}", missing.join(", ")))
    } else if db.port == 0 {
        Check::new(Status::Fail, label, "port must not be 0")
    } else {
        Check::new(
            Status::Pass,
            label,
            format!("{}@{}:{}/{}", db.username, db.host, db.port, db.name),
        )
    }
}

fn check_secrets(dir: &Path) -> Section {
    let store = SecretStore::new(dir);
    let mut checks = vec![check_secrets_dir(dir)];
    checks.extend(
        Secret::ALL
            .iter()
            .map(|&secret| check_secret(&store, secret)),
    );

    Section {
        title: format!("Secrets ({})", dir.display()),
        checks,
    }
}

fn check_secrets_dir(dir: &Path) -> Check {
    let label = "directory";
    let metadata = match fs::metadata(dir) {
        Ok(metadata) => metadata,
        Err(e) => return Check::new(Status::Fail, label, format!("cannot open: {e}")),
    };
    if !metadata.is_dir() {
        return Check::new(Status::Fail, label, "not a directory");
    }

    let mode = metadata.permissions().mode() & 0o777;
    if mode & 0o077 != 0 {
        Check::new(
            Status::Warn,
            label,
            format!("mode {mode:o}, accessible to other users"),
        )
    } else {
        Check::new(Status::Pass, label, format!("mode {mode:o}"))
    }
}

fn check_secret(store: &SecretStore, secret: Secret) -> Check {
    let label = secret.name();
    let weakness = match store.inspect(secret) {
        Ok(weakness) => weakness,
        Err(e) => return Check::new(Status::Fail, label, format!("{e:#}")),
    };

    let mut issues: Vec<String> = weakness.into_iter().collect();
    // inspect() already read the file, so its metadata is only missing on a race we can ignore.
    let mode = fs::metadata(store.path(secret))
        .map(|m| m.permissions().mode() & 0o777)
        .unwrap_or(0);
    if mode & 0o077 != 0 {
        issues.push(format!("mode {mode:o} is readable by other users"));
    }

    if issues.is_empty() {
        Check::new(Status::Pass, label, "valid")
    } else {
        Check::new(
            Status::Warn,
            label,
            format!("valid, but {}", issues.join("; ")),
        )
    }
}

fn print_report(sections: &[Section]) {
    let heading = Style::new().bold().underline();
    let dim = Style::new().dimmed();
    let width = sections
        .iter()
        .flat_map(|s| &s.checks)
        .map(|c| c.label.chars().count())
        .max()
        .unwrap_or(0);

    for section in sections {
        println!("{heading}{}{heading:#}", section.title);
        for check in &section.checks {
            let style = check.status.style();
            println!(
                "  {style}{}{style:#} {:<width$}  {dim}{}{dim:#}",
                check.status.symbol(),
                check.label,
                check.detail,
            );
        }
        println!();
    }

    let errors: usize = sections.iter().map(|s| s.count(Status::Fail)).sum();
    let warnings: usize = sections.iter().map(|s| s.count(Status::Warn)).sum();
    let status = match (errors, warnings) {
        (0, 0) => Status::Pass,
        (0, _) => Status::Warn,
        _ => Status::Fail,
    };
    let summary = match (errors, warnings) {
        (0, 0) => "All checks passed".to_string(),
        (0, _) => format!("{warnings} {}", plural(warnings, "warning")),
        (_, 0) => format!("{errors} {}", plural(errors, "error")),
        _ => format!(
            "{errors} {}, {warnings} {}",
            plural(errors, "error"),
            plural(warnings, "warning"),
        ),
    };
    let style = status.style();
    println!("{style}{} {summary}{style:#}", status.symbol());
}

fn plural(n: usize, word: &str) -> String {
    if n == 1 {
        word.to_string()
    } else {
        format!("{word}s")
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use secrecy::ExposeSecret;
    use std::fs::Permissions;

    const VALID: &str = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    fn database() -> DatabaseConfig {
        DatabaseConfig {
            host: "postgres".into(),
            port: 5432,
            name: "keygo".into(),
            username: "keygo".into(),
        }
    }

    fn secrets_dir(mode: u32) -> tempfile::TempDir {
        let dir = tempfile::tempdir().unwrap();
        fs::set_permissions(dir.path(), Permissions::from_mode(mode)).unwrap();
        dir
    }

    fn write_secret(dir: &Path, secret: Secret, contents: &str, mode: u32) {
        let path = dir.join(secret.name());
        fs::write(&path, contents).unwrap();
        fs::set_permissions(&path, Permissions::from_mode(mode)).unwrap();
    }

    #[test]
    fn development_environment_warns() {
        let check = check_environment(Environment::Development);

        assert_eq!(check.status, Status::Warn);
    }

    #[test]
    fn https_url_passes_in_production() {
        let check = check_public_url("https://keygo.example", Environment::Production);

        assert_eq!(check.status, Status::Pass);
    }

    #[test]
    fn http_url_passes_in_development() {
        let check = check_public_url("http://localhost", Environment::Development);

        assert_eq!(check.status, Status::Pass);
    }

    #[test]
    fn http_url_fails_in_production() {
        let check = check_public_url("http://keygo.example", Environment::Production);

        assert_eq!(check.status, Status::Fail);
    }

    #[test]
    fn unparseable_url_fails() {
        let check = check_public_url("keygo.example", Environment::Development);

        assert_eq!(check.status, Status::Fail);
    }

    #[test]
    fn non_http_scheme_fails() {
        let check = check_public_url("ftp://keygo.example", Environment::Development);

        assert_eq!(check.status, Status::Fail);
    }

    #[test]
    fn open_signups_warn_only_in_production() {
        assert_eq!(
            check_signups(true, Environment::Production).status,
            Status::Warn
        );
        assert_eq!(
            check_signups(true, Environment::Development).status,
            Status::Pass
        );
        assert_eq!(
            check_signups(false, Environment::Production).status,
            Status::Pass
        );
    }

    #[test]
    fn complete_database_config_passes() {
        let check = check_database(&database());

        assert_eq!(check.status, Status::Pass);
        assert_eq!(check.detail, "keygo@postgres:5432/keygo");
    }

    #[test]
    fn database_config_reports_every_empty_field() {
        let db = DatabaseConfig {
            host: " ".into(),
            username: String::new(),
            ..database()
        };

        let check = check_database(&db);

        assert_eq!(check.status, Status::Fail);
        assert_eq!(check.detail, "empty host, username");
    }

    #[test]
    fn database_port_zero_fails() {
        let db = DatabaseConfig {
            port: 0,
            ..database()
        };

        assert_eq!(check_database(&db).status, Status::Fail);
    }

    #[test]
    fn missing_secrets_dir_fails() {
        let dir = tempfile::tempdir().unwrap();

        let check = check_secrets_dir(&dir.path().join("absent"));

        assert_eq!(check.status, Status::Fail);
    }

    #[test]
    fn owner_only_secrets_dir_passes() {
        let dir = secrets_dir(0o700);

        assert_eq!(check_secrets_dir(dir.path()).status, Status::Pass);
    }

    #[test]
    fn shared_secrets_dir_warns() {
        let dir = secrets_dir(0o755);

        assert_eq!(check_secrets_dir(dir.path()).status, Status::Warn);
    }

    #[test]
    fn valid_owner_only_secret_passes() {
        let dir = secrets_dir(0o700);
        write_secret(dir.path(), Secret::DbPassword, VALID, 0o400);

        let check = check_secret(&SecretStore::new(dir.path()), Secret::DbPassword);

        assert_eq!(check.status, Status::Pass);
    }

    #[test]
    fn valid_but_shared_secret_warns() {
        let dir = secrets_dir(0o700);
        write_secret(dir.path(), Secret::DbPassword, VALID, 0o644);

        let check = check_secret(&SecretStore::new(dir.path()), Secret::DbPassword);

        assert_eq!(check.status, Status::Warn);
    }

    #[test]
    fn missing_secret_fails() {
        let dir = secrets_dir(0o700);

        let check = check_secret(&SecretStore::new(dir.path()), Secret::DbPassword);

        assert_eq!(check.status, Status::Fail);
    }

    #[test]
    fn empty_secret_fails() {
        let dir = secrets_dir(0o700);
        write_secret(dir.path(), Secret::DbPassword, "\n", 0o400);

        let check = check_secret(&SecretStore::new(dir.path()), Secret::DbPassword);

        assert_eq!(check.status, Status::Fail);
    }

    #[test]
    fn weak_secret_warns_without_revealing_its_value() {
        let dir = secrets_dir(0o700);
        write_secret(dir.path(), Secret::DbPassword, "hunter2", 0o400);

        let check = check_secret(&SecretStore::new(dir.path()), Secret::DbPassword);

        assert_eq!(check.status, Status::Warn);
        assert!(!check.detail.contains("hunter2"));
    }

    #[test]
    fn generated_opaque_setup_passes() {
        let dir = secrets_dir(0o700);
        let value = Secret::OpaqueSetup.generate().unwrap();
        write_secret(
            dir.path(),
            Secret::OpaqueSetup,
            value.expose_secret(),
            0o400,
        );

        let check = check_secret(&SecretStore::new(dir.path()), Secret::OpaqueSetup);

        assert_eq!(check.status, Status::Pass);
    }

    #[test]
    fn corrupt_opaque_setup_fails() {
        let dir = secrets_dir(0o700);
        write_secret(dir.path(), Secret::OpaqueSetup, VALID, 0o400);

        let check = check_secret(&SecretStore::new(dir.path()), Secret::OpaqueSetup);

        assert_eq!(check.status, Status::Fail);
    }
}

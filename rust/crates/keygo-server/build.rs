// sqlx::migrate!() embeds migrations/ at compile time; rebuild when a migration is added.
fn main() {
    println!("cargo:rerun-if-changed=migrations");
}

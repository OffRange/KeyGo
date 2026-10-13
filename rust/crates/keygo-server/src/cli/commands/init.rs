use crate::config::Config;
use crate::secrets::{CreateOutcome, Secret, SecretStore};
use anyhow::Context;

pub fn run(config: &Config) -> anyhow::Result<()> {
    let store = SecretStore::new(&config.secrets_dir);
    store.create_dir()?;

    for &secret in Secret::ALL {
        let status = match store.create(secret, &secret.generate()?)? {
            CreateOutcome::Created => "created",
            CreateOutcome::AlreadyExists => {
                store
                    .load(secret)
                    .context("refusing to overwrite an existing secret")?;
                "exists"
            }
        };
        println!("{:<20} {status}", secret.name());
    }
    Ok(())
}

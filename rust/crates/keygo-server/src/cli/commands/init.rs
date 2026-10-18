use crate::config::SecretsConfig;
use crate::secrets::{CreateOutcome, Secret};
use anyhow::Context;
use clap::Args;

#[derive(Args)]
pub struct InitArgs {
    #[command(flatten)]
    secrets: SecretsConfig,
}

pub fn run(args: &InitArgs) -> anyhow::Result<()> {
    let store = args.secrets.store();
    store.create_dir()?;

    for &secret in Secret::ALL {
        let status = match store.create(secret, &secret.generate()?)? {
            CreateOutcome::Created => "created",
            CreateOutcome::AlreadyExists => {
                store
                    .inspect(secret)
                    .context("refusing to overwrite an existing secret")?;
                "exists"
            }
        };
        println!("{:<20} {status}", secret.name());
    }

    Ok(())
}

use serde::{Deserialize, Serialize};
use uuid::Uuid;

macro_rules! id {
    ($($name:ident),+) => {
        $(#[derive(Serialize, Deserialize, Clone, Copy, PartialEq, Eq, Hash, Debug)]
        #[serde(transparent)]
        pub struct $name(pub Uuid);

        impl $name {
            pub fn random() -> Self {
                Self(Uuid::new_v4())
            }

            pub const fn as_uuid(self) -> Uuid {
                self.0
            }
        }

        impl From<Uuid> for $name {
            fn from(value: Uuid) -> Self {
                Self(value)
            }
        })+
    };
}

id!(UserId, VaultId, ItemId, NamespaceId);

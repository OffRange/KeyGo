use serde::Serialize;
use uuid::Uuid;

macro_rules! id {
    ($($name:ident),+) => {
        $(#[derive(Serialize, Clone, Copy, PartialEq, Eq, Hash, Debug)]
        #[serde(transparent)]
        pub struct $name(pub Uuid);

        impl $name {
            pub fn new_v4() -> Self {
                Self(Uuid::new_v4())
            }
        })+
    };
}

id!(UserId, VaultId, ItemId);

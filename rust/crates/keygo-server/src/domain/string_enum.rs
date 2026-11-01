use thiserror::Error;

#[derive(Debug, Error)]
#[error("unknown {kind} {value:?}")]
pub struct UnknownVariant {
    kind: &'static str,
    value: String,
}

impl UnknownVariant {
    pub(super) fn new(kind: &'static str, value: &str) -> Self {
        Self {
            kind,
            value: value.to_owned(),
        }
    }
}

macro_rules! string_enum {
    (
        $(#[$attr:meta])*
        $vis:vis enum $name:ident { $($variant:ident => $str:literal),+ $(,)? }
    ) => {
        $(#[$attr])*
        #[derive(Debug, Clone, Copy, PartialEq, Eq)]
        $vis enum $name {
            $($variant,)+
        }

        impl $name {
            #[cfg(test)]
            #[allow(dead_code, reason = "only enums with a round-trip test iterate their variants")]
            pub const ALL: &[Self] = &[$(Self::$variant,)+];

            pub const fn as_str(self) -> &'static str {
                match self {
                    $(Self::$variant => $str,)+
                }
            }
        }

        impl ::std::str::FromStr for $name {
            type Err = $crate::domain::string_enum::UnknownVariant;

            fn from_str(s: &str) -> Result<Self, Self::Err> {
                match s {
                    $($str => Ok(Self::$variant),)+
                    _ => Err($crate::domain::string_enum::UnknownVariant::new(stringify!($name), s)),
                }
            }
        }

        impl ::serde::Serialize for $name {
            fn serialize<S: ::serde::Serializer>(&self, serializer: S) -> Result<S::Ok, S::Error> {
                serializer.serialize_str(self.as_str())
            }
        }

        impl<'de> ::serde::Deserialize<'de> for $name {
            fn deserialize<D: ::serde::Deserializer<'de>>(deserializer: D) -> Result<Self, D::Error> {
                <String as ::serde::Deserialize>::deserialize(deserializer)?
                    .parse()
                    .map_err(::serde::de::Error::custom)
            }
        }
    };
}

pub(super) use string_enum;

#[cfg(test)]
mod tests {
    use serde_json::json;

    string_enum! {
        enum Fruit {
            Apple => "apple",
            BloodOrange => "blood_orange",
        }
    }

    #[test]
    fn every_variant_round_trips_through_str_and_json() {
        for &fruit in Fruit::ALL {
            assert_eq!(fruit.as_str().parse::<Fruit>().unwrap(), fruit);

            let encoded = serde_json::to_value(fruit).unwrap();
            assert_eq!(encoded, json!(fruit.as_str()));
            assert_eq!(serde_json::from_value::<Fruit>(encoded).unwrap(), fruit);
        }
    }

    #[test]
    fn unknown_names_are_rejected() {
        assert!("pear".parse::<Fruit>().is_err());
        assert!(serde_json::from_value::<Fruit>(json!("BloodOrange")).is_err());
    }
}

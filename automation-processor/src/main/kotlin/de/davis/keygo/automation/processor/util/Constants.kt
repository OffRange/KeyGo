package de.davis.keygo.automation.processor.util

object Constants {
    object Prefixes {
        const val VAULT_PREFIX = "Vault"
    }

    object Suffixes {
        const val MAPPER_SUFFIX = "Mapper"
        const val ENTITY_SUFFIX = "Entity"
        const val ENUM_SUFFIX = "Enum"
    }

    object Packages {
        const val ENUM_PACKAGE_SUFFIX = "item"
        const val MAPPER_PACKAGE_SUFFIX = "$ENUM_PACKAGE_SUFFIX.data.mapper"
        const val ENTITY_PACKAGE_SUFFIX = "$ENUM_PACKAGE_SUFFIX.data.local.entity"
        const val RELATION_PACKAGE_SUFFIX = "$ENUM_PACKAGE_SUFFIX.data.local.relation"
    }
    
    object MapperNames {
        const val TO_DATA = "toData"
        const val TO_DOMAIN = "toDomain"
    }
}

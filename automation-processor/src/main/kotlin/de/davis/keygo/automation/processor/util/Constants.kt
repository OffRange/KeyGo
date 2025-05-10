package de.davis.keygo.automation.processor.util

object Constants {
    object Suffixes {
        const val ENTITY_SUFFIX = "Entity"
        const val ENUM_SUFFIX = "Enum"
    }

    object Packages {
        const val ENUM_PACKAGE_SUFFIX = "item"
        const val ENTITY_PACKAGE_SUFFIX = "$ENUM_PACKAGE_SUFFIX.data.local.model"
    }

    object ColumnNames {
        const val VAULT_ID = "vaultId"
    }
}

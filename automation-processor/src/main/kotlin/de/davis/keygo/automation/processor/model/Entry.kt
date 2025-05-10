package de.davis.keygo.automation.processor.model

import de.davis.keygo.automation.processor.util.Constants
import de.davis.keygo.automation.processor.util.GetClassName
import de.davis.keygo.processor.annotation.VaultEntity
import org.koin.core.component.KoinComponent

sealed class Entry : KoinComponent {

    abstract val simpleName: String
    abstract val packageName: String
    abstract val properties: Sequence<Property>

    data class RootEntry(
        override val simpleName: String,
        override val packageName: String,
        override val properties: Sequence<Property>,
        val children: List<ChildEntry>
    ) : Entry() {

        val idProperty by lazy {
            properties.firstOrNull { it.isId }
                ?: throw IllegalStateException("No ID property found in $simpleName")
        }

        fun enumClassName(getClassName: GetClassName) = getClassName(
            "$simpleName${Constants.Suffixes.ENUM_SUFFIX}",
            packageNameSuffix = Constants.Packages.ENUM_PACKAGE_SUFFIX
        )
    }

    data class ChildEntry(
        override val simpleName: String,
        override val packageName: String,
        override val properties: Sequence<Property>,
        val vaultEntity: VaultEntity,
    ) : Entry() {

        val rootVaultId: String = Constants.ColumnNames.VAULT_ID
    }

    fun entityClassName(getClassName: GetClassName) = getClassName(
        "$simpleName${Constants.Suffixes.ENTITY_SUFFIX}",
        packageNameSuffix = Constants.Packages.ENTITY_PACKAGE_SUFFIX
    )
}

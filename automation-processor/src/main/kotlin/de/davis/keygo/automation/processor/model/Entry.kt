package de.davis.keygo.automation.processor.model

import com.squareup.kotlinpoet.ClassName
import de.davis.keygo.automation.processor.util.Constants
import de.davis.keygo.automation.processor.util.GetClassName
import de.davis.keygo.processor.annotation.VaultEntity
import org.koin.core.component.KoinComponent

sealed class Entry : KoinComponent {

    abstract val simpleName: String
    abstract val packageName: String
    abstract val properties: Sequence<Property>

    val className get() = ClassName(packageName, simpleName)

    data class RootEntry(
        override val simpleName: String,
        override val packageName: String,
        override val properties: Sequence<Property>,
        val basicClassName: ClassName?,
        val children: List<ChildEntry>
    ) : Entry() {

        val idProperty by lazy {
            properties.firstOrNull { it.isId }
                ?: throw IllegalStateException("No ID property found in $simpleName")
        }

        fun embeddedName(getClassName: GetClassName) = entityClassName(getClassName)
            .simpleName
            .replaceFirstChar { it.lowercase() }

        fun enumTypeClassName(getClassName: GetClassName) = getClassName(
            "$simpleName${Constants.Suffixes.TYPE_SUFFIX}",
            packageNameSuffix = Constants.Packages.ENUM_PACKAGE_SUFFIX
        )
    }

    data class ChildEntry(
        override val simpleName: String,
        override val packageName: String,
        override val properties: Sequence<Property>,
        val rootId: Property,
        val vaultEntity: VaultEntity,
    ) : Entry() {
        val relationPropertyName by lazy {
            simpleName.replaceFirstChar { it.lowercase() }
        }

        fun relationClassName(getClassName: GetClassName) = getClassName(
            "${Constants.Prefixes.VAULT_PREFIX}$simpleName",
            packageNameSuffix = Constants.Packages.RELATION_PACKAGE_SUFFIX
        )
    }

    fun entityClassName(getClassName: GetClassName) = getClassName(
        "$simpleName${Constants.Suffixes.ENTITY_SUFFIX}",
        packageNameSuffix = Constants.Packages.ENTITY_PACKAGE_SUFFIX
    )

    fun mapperClassName(getClassName: GetClassName) = getClassName(
        "$simpleName${Constants.Suffixes.MAPPER_SUFFIX}",
        packageNameSuffix = Constants.Packages.MAPPER_PACKAGE_SUFFIX
    )
}

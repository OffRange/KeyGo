package de.davis.keygo.automation.processor.writer

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo
import de.davis.keygo.automation.processor.ext.getAnnotation
import de.davis.keygo.automation.processor.model.EntityProperty
import de.davis.keygo.automation.processor.model.ForeignKey
import de.davis.keygo.automation.processor.model.Index
import de.davis.keygo.automation.processor.util.Constants.ColumnNames
import de.davis.keygo.automation.processor.util.Constants.Packages
import de.davis.keygo.automation.processor.util.Constants.Suffixes
import de.davis.keygo.automation.processor.util.ENTITY_CLASS_NAME
import de.davis.keygo.automation.processor.util.GetClassName
import de.davis.keygo.automation.processor.util.StringUtils.camelToSnakeCase
import de.davis.keygo.automation.processor.util.StringUtils.isCamelCase
import de.davis.keygo.automation.processor.util.primaryRoomKey
import de.davis.keygo.automation.processor.util.roomColumnInfo
import de.davis.keygo.automation.processor.util.roomEntity
import de.davis.keygo.processor.annotation.Id

class EntityWriter(
    private val codeGenerator: CodeGenerator,
    private val className: GetClassName
) {

    fun write(symbol: KSClassDeclaration, root: KSClassDeclaration? = null) {
        val name = "${symbol.simpleName.getShortName()}${Suffixes.ENTITY_SUFFIX}"
        val className = className(name, packageNameSuffix = Packages.ENTITY_PACKAGE_SUFFIX)
        val fileSpec = FileSpec.builder(className)

        val constructorSpec = FunSpec.constructorBuilder()
        val properties = collectProperties(symbol, constructorSpec)

        if (root != null) {
            addVaultIdProperty(properties, constructorSpec)
        }

        val typeSpec = TypeSpec.classBuilder(className)
            .addModifiers(KModifier.DATA, KModifier.INTERNAL)
            .apply {
                if (root != null) {
                    addRootEntityAnnotation(root)
                } else {
                    addAnnotation(ENTITY_CLASS_NAME)
                }
            }
            .primaryConstructor(constructorSpec.build())
            .addProperties(properties)
            .build()

        fileSpec.addType(typeSpec)
            .build()
            .writeTo(codeGenerator, Dependencies.ALL_FILES)
    }

    private fun collectProperties(
        symbol: KSClassDeclaration,
        constructorSpec: FunSpec.Builder
    ): MutableList<PropertySpec> {
        val properties = mutableListOf<PropertySpec>()

        symbol.getAllProperties()
            .filter { it.findOverridee() == null }
            .forEach { property ->
                val propertyInfo = processProperty(property)

                val parameterSpec = ParameterSpec.builder(propertyInfo.name, propertyInfo.type)
                    .apply {
                        if (propertyInfo.annotation != null) addAnnotation(propertyInfo.annotation)

                        if (propertyInfo.name.isCamelCase())
                            addAnnotation(roomColumnInfo(name = propertyInfo.name.camelToSnakeCase()))
                    }
                    .build()

                val propertySpec = PropertySpec.builder(propertyInfo.name, propertyInfo.type)
                    .initializer(propertyInfo.name)
                    .build()

                constructorSpec.addParameter(parameterSpec)
                properties.add(propertySpec)
            }

        return properties
    }

    private fun processProperty(property: com.google.devtools.ksp.symbol.KSPropertyDeclaration): EntityProperty {
        val (name, annotation) = property.getAnnotation<Id>()?.let {
            ColumnNames.ID to primaryRoomKey()
        } ?: (property.simpleName.getShortName() to null)

        val typeName = property.type.resolve().toTypeName()

        return EntityProperty(name, typeName, annotation)
    }

    private fun addVaultIdProperty(
        properties: MutableList<PropertySpec>,
        constructorSpec: FunSpec.Builder
    ) {
        val vaultIdProperty = PropertySpec.builder(ColumnNames.VAULT_ID, Long::class)
            .initializer(ColumnNames.VAULT_ID)
            .build()
        properties.add(vaultIdProperty)

        constructorSpec.addParameter(
            ParameterSpec.builder(ColumnNames.VAULT_ID, Long::class)
                .addAnnotation(roomColumnInfo(name = ColumnNames.VAULT_ID.camelToSnakeCase()))
                .build()
        )
    }

    private fun TypeSpec.Builder.addRootEntityAnnotation(root: KSClassDeclaration) {
        val rootName = "${root.simpleName.getShortName()}${Suffixes.ENTITY_SUFFIX}"
        val rootClassName = className(rootName, packageNameSuffix = Packages.ENTITY_PACKAGE_SUFFIX)
        addAnnotation(
            roomEntity(
                ForeignKey(
                    entity = rootClassName,
                    parentColumns = listOf(ColumnNames.ID),
                    childColumns = listOf(ColumnNames.VAULT_ID.camelToSnakeCase()),
                ),
                Index(
                    value = listOf(ColumnNames.VAULT_ID.camelToSnakeCase()),
                    unique = true
                )
            )
        )
    }
}


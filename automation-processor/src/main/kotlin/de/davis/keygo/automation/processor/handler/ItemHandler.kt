package de.davis.keygo.automation.processor.handler

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.isAnnotationPresent
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.Modifier
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.TypeName
import de.davis.keygo.automation.processor.exception.NotFoundException
import de.davis.keygo.automation.processor.ext.getAnnotation
import de.davis.keygo.automation.processor.kotlinpoet.FileBuilder
import de.davis.keygo.automation.processor.kotlinpoet.dataClass
import de.davis.keygo.automation.processor.kotlinpoet.enum
import de.davis.keygo.automation.processor.kotlinpoet.file
import de.davis.keygo.automation.processor.model.Entry
import de.davis.keygo.automation.processor.model.ForeignKey
import de.davis.keygo.automation.processor.model.Index
import de.davis.keygo.automation.processor.model.getOwnProperties
import de.davis.keygo.automation.processor.util.COMPOSABLE_CLASS_NAME
import de.davis.keygo.automation.processor.util.Constants
import de.davis.keygo.automation.processor.util.EMBEDDED_CLASS_NAME
import de.davis.keygo.automation.processor.util.GetClassName
import de.davis.keygo.automation.processor.util.STRING_RESOURCE_MEMBER_NAME
import de.davis.keygo.automation.processor.util.StringUtils.camelToSnakeCase
import de.davis.keygo.automation.processor.util.StringUtils.isCamelCase
import de.davis.keygo.automation.processor.util.primaryRoomKey
import de.davis.keygo.automation.processor.util.roomColumnInfo
import de.davis.keygo.automation.processor.util.roomEntity
import de.davis.keygo.automation.processor.util.roomRelation
import de.davis.keygo.automation.processor.util.stringRes
import de.davis.keygo.processor.annotation.Ignore
import de.davis.keygo.processor.annotation.RootVaultEntity
import de.davis.keygo.processor.annotation.VaultEntity
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ItemHandler : Handler<KSClassDeclaration, RootVaultEntity>, KoinComponent {

    private val className by inject<GetClassName>()
    private val codeGenerator by inject<CodeGenerator>()

    private val roots = mutableListOf<Entry.RootEntry>()

    override fun filter(node: KSClassDeclaration): Boolean {
        return node.classKind == ClassKind.INTERFACE && node.modifiers.contains(Modifier.SEALED)
    }

    @OptIn(KspExperimental::class)
    override fun handleSymbols(symbols: List<KSClassDeclaration>): List<KSAnnotated> {
        symbols.map { root ->
            val rootProperties = root.getOwnProperties()
            val rootId = rootProperties.first { it.isId }

            val subclasses = root.getSealedSubclasses()
                .filterNot { it.isAnnotationPresent(Ignore::class) }
                .map {
                    Entry.ChildEntry(
                        simpleName = it.simpleName.asString(),
                        packageName = it.packageName.asString(),
                        vaultEntity = it.getAnnotation<VaultEntity>() ?: throw NotFoundException(
                            "Annotation @VaultEntity is missing on ${it.simpleName.asString()}"
                        ),
                        properties = it.getOwnProperties(),
                        rootId = rootId
                    )
                }
                .toList()

            Entry.RootEntry(
                simpleName = root.simpleName.asString(),
                packageName = root.packageName.asString(),
                properties = rootProperties,
                children = subclasses
            )
        }.also(roots::addAll)

        writeEnum(roots)
        writeEntities(roots)
        writeRelations(roots)
        writeMappers(roots)

        return emptyList()
    }

    fun writeEnum(roots: List<Entry.RootEntry>) {
        roots.forEach { root ->
            val rootClassName = root.enumClassName(getClassName = className)

            file(
                codeGenerator = codeGenerator,
                className = rootClassName
            ) { className ->
                enum(className) {
                    constructor {
                        parameter("resString", Int::class, KModifier.INTERNAL)
                    }

                    root.children.forEach {
                        entry(it.simpleName) {
                            parameter(stringRes(it.vaultEntity.resString))
                        }
                    }
                }

                val funSpec = FunSpec.builder("getString")
                    .addAnnotation(COMPOSABLE_CLASS_NAME)
                    .receiver(rootClassName)
                    .returns(String::class)
                    .addStatement("return %M(resString)", STRING_RESOURCE_MEMBER_NAME)

                fileSpecBuilder.addFunction(funSpec.build())
            }
        }
    }

    fun writeEntities(roots: List<Entry.RootEntry>) {
        roots.forEach { root ->
            file(
                codeGenerator = codeGenerator,
                className = root.entityClassName(getClassName = className)
            ) {
                dataClass(root.entityClassName(getClassName = className)) {
                    annotation(roomEntity())

                    constructor {
                        root.properties.forEach {
                            val annotations = mutableListOf<AnnotationSpec>()
                            if (it.isId) annotations.add(primaryRoomKey())
                            if (it.name.isCamelCase()) annotations.add(roomColumnInfo(it.name.camelToSnakeCase()))

                            parameter(it.name, it.type, annotations)
                        }
                    }
                }
            }

            root.children.forEach { subclass ->
                file(
                    codeGenerator = codeGenerator,
                    className = subclass.entityClassName(getClassName = className)
                ) {
                    dataClass(subclass.entityClassName(getClassName = className)) {
                        annotation(
                            annotationSpec = roomEntity(
                                foreignKey = ForeignKey(
                                    entity = root.entityClassName(getClassName = className),
                                    parentColumns = listOf(root.idProperty.name.camelToSnakeCase()),
                                    childColumns = listOf(subclass.rootId.name.camelToSnakeCase()),
                                ),
                                index = Index(
                                    value = listOf(subclass.rootId.name.camelToSnakeCase()),
                                    unique = true,
                                )
                            )
                        )

                        constructor {
                            subclass.properties.forEach {
                                val annotations = mutableListOf<AnnotationSpec>()
                                if (it.isId) annotations.add(primaryRoomKey())
                                if (it.name.isCamelCase()) annotations.add(roomColumnInfo(it.name.camelToSnakeCase()))

                                parameter(it.name, it.type, annotations)
                            }

                            parameter(
                                subclass.rootId.name,
                                subclass.rootId.type,
                                listOf(roomColumnInfo(name = subclass.rootId.name.camelToSnakeCase()))
                            )
                        }
                    }
                }
            }
        }
    }

    fun writeRelations(roots: List<Entry.RootEntry>) {
        roots.forEach { root ->
            val rootClassName = root.entityClassName(getClassName = className)

            root.children.forEach { subclass ->
                file(
                    codeGenerator = codeGenerator,
                    className = subclass.relationClassName(getClassName = className)
                ) {
                    dataClass(subclass.relationClassName(getClassName = className)) {
                        constructor {
                            parameter(
                                name = root.embeddedName(getClassName = className),
                                type = rootClassName,
                                annotations = listOf(
                                    AnnotationSpec.builder(EMBEDDED_CLASS_NAME).build()
                                )
                            )

                            parameter(
                                name = subclass.relationPropertyName,
                                type = subclass.entityClassName(getClassName = className),
                                annotations = listOf(
                                    roomRelation(
                                        parentColumn = root.idProperty.name.camelToSnakeCase(),
                                        entityColumn = subclass.rootId.name.camelToSnakeCase()
                                    )
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    fun writeMappers(roots: List<Entry.RootEntry>) {
        roots.forEach { root ->
            file(
                codeGenerator = codeGenerator,
                className = root.mapperClassName(getClassName = className)
            ) {
                writeMapperFunction(
                    type = MapperType.TO_DATA,
                    root = root
                )
                writeMapperFunction(
                    type = MapperType.TO_DOMAIN,
                    root = root
                )
            }

            root.children.forEach { subclass ->
                file(
                    codeGenerator = codeGenerator,
                    className = subclass.mapperClassName(getClassName = className)
                ) {
                    writeMapperFunction(
                        type = MapperType.TO_DATA,
                        root = root,
                        child = subclass
                    )
                    writeMapperFunction(
                        type = MapperType.TO_DOMAIN,
                        root = root,
                        child = subclass
                    )

                    mapperFunction(
                        name = MapperType.TO_DOMAIN.funName,
                        receiver = subclass.relationClassName(getClassName = className),
                        returnType = subclass.className
                    ) {
                        `return`(
                            "%L.%L(%L)",
                            subclass.relationPropertyName,
                            MapperType.TO_DOMAIN.funName,
                            root.embeddedName(getClassName = className)
                        )
                    }
                }
            }
        }
    }


    private fun FileBuilder.writeMapperFunction(
        type: MapperType,
        root: Entry.RootEntry,
    ) {
        val (receiver, returnType) = when (type) {
            MapperType.TO_DATA -> root.className to root.entityClassName(getClassName = className)
            MapperType.TO_DOMAIN -> root.entityClassName(getClassName = className) to root.className
        }

        val fields = root.properties.associate {
            it.name to it.name
        }

        writeMapperFunction(
            funName = type.funName,
            receiver = receiver,
            returnType = returnType,
            fields = fields,
        )
    }

    private fun FileBuilder.writeMapperFunction(
        type: MapperType,
        root: Entry.RootEntry,
        child: Entry.ChildEntry,
    ) {
        val (receiver, returnType) = when (type) {
            MapperType.TO_DATA -> child.className to child.entityClassName(getClassName = className)
            MapperType.TO_DOMAIN -> child.entityClassName(getClassName = className) to child.className
        }

        val fields = child.properties.associate {
            it.name to it.name
        }.toMutableMap()

        val parameter = when (type) {
            MapperType.TO_DATA -> {
                fields.put(child.rootId.name, child.rootId.name)
                null
            }

            MapperType.TO_DOMAIN -> {
                val rootItem = root.entityClassName(getClassName = className)
                    .simpleName
                    .replaceFirstChar { it.lowercase() }
                root.properties.associate {
                    it.name to "$rootItem.${it.name}"
                }.also(fields::putAll)
                rootItem to root.entityClassName(getClassName = className)
            }
        }

        writeMapperFunction(
            funName = type.funName,
            receiver = receiver,
            returnType = returnType,
            fields = fields,
            parameter = parameter
        )
    }

    private fun FileBuilder.writeMapperFunction(
        funName: String,
        receiver: TypeName,
        returnType: TypeName,
        fields: Map<String, String>,
        parameter: Pair<String, TypeName>? = null,
    ) {
        mapperFunction(
            name = funName,
            receiver = receiver,
            returnType = returnType
        ) {
            parameter?.let { (paramName, paramType) ->
                parameter(paramName, paramType)
            }

            `return`("%T(${fields.entries.joinToString(", ")})", returnType)
        }
    }

    enum class MapperType(val funName: String) {
        TO_DATA(Constants.MapperNames.TO_DATA),
        TO_DOMAIN(Constants.MapperNames.TO_DOMAIN)
    }
}

package de.davis.keygo.automation.processor.handler

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.isAnnotationPresent
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.Modifier
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LONG
import de.davis.keygo.automation.processor.exception.NotFoundException
import de.davis.keygo.automation.processor.ext.getAnnotation
import de.davis.keygo.automation.processor.kotlinpoet.dataClass
import de.davis.keygo.automation.processor.kotlinpoet.enum
import de.davis.keygo.automation.processor.kotlinpoet.file
import de.davis.keygo.automation.processor.model.Entry
import de.davis.keygo.automation.processor.model.ForeignKey
import de.davis.keygo.automation.processor.model.Index
import de.davis.keygo.automation.processor.model.getOwnProperties
import de.davis.keygo.automation.processor.util.GetClassName
import de.davis.keygo.automation.processor.util.StringUtils.camelToSnakeCase
import de.davis.keygo.automation.processor.util.StringUtils.isCamelCase
import de.davis.keygo.automation.processor.util.primaryRoomKey
import de.davis.keygo.automation.processor.util.roomColumnInfo
import de.davis.keygo.automation.processor.util.roomEntity
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
            val subclasses = root.getSealedSubclasses()
                .filterNot { it.isAnnotationPresent(Ignore::class) }
                .map {
                    Entry.ChildEntry(
                        simpleName = it.simpleName.asString(),
                        packageName = it.packageName.asString(),
                        vaultEntity = it.getAnnotation<VaultEntity>() ?: throw NotFoundException(
                            "Annotation @VaultEntity is missing on ${it.simpleName.asString()}"
                        ),
                        properties = it.getOwnProperties()
                    )
                }
                .toList()

            Entry.RootEntry(
                simpleName = root.simpleName.asString(),
                packageName = root.packageName.asString(),
                properties = root.getOwnProperties(),
                children = subclasses
            )
        }.also(roots::addAll)

        writeEnum(roots)
        writeEntities(roots)

        return emptyList()
    }

    fun writeEnum(roots: List<Entry.RootEntry>) {
        roots.forEach { root ->
            file(
                codeGenerator = codeGenerator,
                className = root.enumClassName(getClassName = className)
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
                                    childColumns = listOf(subclass.rootVaultId.camelToSnakeCase()),
                                ),
                                index = Index(
                                    value = listOf(subclass.rootVaultId.camelToSnakeCase()),
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
                                subclass.rootVaultId,
                                LONG,
                                listOf(roomColumnInfo(name = subclass.rootVaultId.camelToSnakeCase()))
                            )
                        }
                    }
                }
            }
        }
    }
}

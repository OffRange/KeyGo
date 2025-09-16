package de.davis.keygo.automation.processor.handler

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.Modifier
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.asTypeName
import com.squareup.kotlinpoet.buildCodeBlock
import de.davis.keygo.automation.processor.exception.NotFoundException
import de.davis.keygo.automation.processor.ext.getAnnotation
import de.davis.keygo.automation.processor.kotlinpoet.enum
import de.davis.keygo.automation.processor.kotlinpoet.file
import de.davis.keygo.automation.processor.model.Entry
import de.davis.keygo.automation.processor.util.COMPOSABLE_CLASS_NAME
import de.davis.keygo.automation.processor.util.GetStringRes
import de.davis.keygo.automation.processor.util.ICONS_DEFAULT_CLASS_NAME
import de.davis.keygo.automation.processor.util.IMAGE_VECTOR_CLASS_NAME
import de.davis.keygo.automation.processor.util.STRING_RESOURCE_MEMBER_NAME
import de.davis.keygo.automation.processor.util.defaultIconMemberName
import de.davis.keygo.processor.annotation.RootVaultEntity
import de.davis.keygo.processor.annotation.VaultEntity
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ItemHandler : Handler<KSClassDeclaration, RootVaultEntity>, KoinComponent {

    private val stringRes by inject<GetStringRes>()

    private val codeGenerator by inject<CodeGenerator>()

    private val roots = mutableListOf<Entry.RootEntry>()

    override fun filter(node: KSClassDeclaration): Boolean {
        return node.classKind == ClassKind.INTERFACE && node.modifiers.contains(Modifier.SEALED)
    }

    @OptIn(KspExperimental::class)
    override fun handleSymbols(symbols: List<KSClassDeclaration>): List<KSAnnotated> {
        symbols.map { root ->
            val subclasses = root.getSealedSubclasses().map {
                Entry.ChildEntry(
                    simpleName = it.simpleName.asString(),
                    packageName = it.packageName.asString(),
                    vaultEntity = it.getAnnotation<VaultEntity>() ?: throw NotFoundException(
                        "Annotation @RootVaultEntity is missing on ${it.qualifiedName?.asString()}"
                    ),
                )
            }.toList()

            Entry.RootEntry(
                simpleName = root.simpleName.asString(),
                packageName = root.packageName.asString(),
                children = subclasses
            )
        }.also(roots::addAll)

        writeEnumType(roots)
        return emptyList()
    }

    fun writeEnumType(roots: List<Entry.RootEntry>) {
        roots.forEach { root ->
            val rootClassName = root.enumTypeClassName()

            file(
                codeGenerator = codeGenerator,
                className = rootClassName
            ) { className ->
                enum(className) {
                    /*constructor {
                        parameter("resString", Int::class, KModifier.INTERNAL)
                        parameter("icon", IMAGE_VECTOR_CLASS_NAME, emptyList(), KModifier.INTERNAL)
                    }*/

                    root.children.forEach {
                        entry(it.simpleName)/* {
                            parameter(stringRes(it.vaultEntity.resString))
                            parameter(
                                "%T.Default.%M",
                                ICONS_DEFAULT_CLASS_NAME,
                                defaultIconMemberName(it.vaultEntity.defaultIconType)
                            )
                        }*/
                    }
                }
            }

            file(
                codeGenerator = codeGenerator,
                className = ClassName(
                    rootClassName.packageName.substringBefore("domain") + "presentation",
                    rootClassName.simpleName
                )
            ) { className ->
                val funSpec = FunSpec.getterBuilder()
                    .addAnnotation(COMPOSABLE_CLASS_NAME)
                    .addCode(
                        buildCodeBlock {
                            beginControlFlow("return when(this)")
                            root.children.forEach {
                                addStatement(
                                    "%T.%L -> %M(%L) to %T.Default.%M",
                                    rootClassName,
                                    it.simpleName,
                                    STRING_RESOURCE_MEMBER_NAME,
                                    stringRes(it.vaultEntity.resString),
                                    ICONS_DEFAULT_CLASS_NAME,
                                    defaultIconMemberName(it.vaultEntity.defaultIconType)
                                )
                            }
                            endControlFlow()
                        }
                    ).build()


                val returnType = Pair::class
                    .asTypeName()
                    .parameterizedBy(
                        STRING,
                        IMAGE_VECTOR_CLASS_NAME
                    )

                val propertySpec = PropertySpec.builder("presentation", returnType)
                    .getter(funSpec)
                    .receiver(rootClassName)
                    .build()

                fileSpecBuilder.addProperty(propertySpec)
            }
        }
    }
}

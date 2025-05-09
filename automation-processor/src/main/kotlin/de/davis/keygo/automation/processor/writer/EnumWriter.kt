package de.davis.keygo.automation.processor.writer

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.isAnnotationPresent
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.writeTo
import de.davis.keygo.automation.processor.exception.NotFoundException
import de.davis.keygo.automation.processor.ext.getAnnotation
import de.davis.keygo.automation.processor.util.COMPOSABLE_CLASS_NAME
import de.davis.keygo.automation.processor.util.Constants.ErrorMessages
import de.davis.keygo.automation.processor.util.Constants.Packages
import de.davis.keygo.automation.processor.util.Constants.Suffixes
import de.davis.keygo.automation.processor.util.GetClassName
import de.davis.keygo.automation.processor.util.STRING_RESOURCE_MEMBER_NAME
import de.davis.keygo.automation.processor.util.stringRes
import de.davis.keygo.processor.annotation.Ignore
import de.davis.keygo.processor.annotation.VaultEntity

class EnumWriter(
    private val codeGenerator: CodeGenerator,
    private val className: GetClassName
) {

    @OptIn(KspExperimental::class)
    fun write(symbol: KSClassDeclaration) {
        val name = "${symbol.simpleName.getShortName()}${Suffixes.ENUM_SUFFIX}"
        val className = className(name, packageNameSuffix = Packages.ENUM_PACKAGE_SUFFIX)
        val fileSpec = FileSpec.builder(className)

        val enumSpec = createEnumTypeSpec(name, symbol)
        val getStringFunction = createGetStringFunction(className)

        fileSpec
            .addType(enumSpec)
            .addFunction(getStringFunction)
            .build()
            .writeTo(codeGenerator, Dependencies.ALL_FILES)
    }

    @OptIn(KspExperimental::class)
    private fun createEnumTypeSpec(name: String, symbol: KSClassDeclaration): TypeSpec {
        return TypeSpec.enumBuilder(name)
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter("resString", Int::class)
                    .build()
            )
            .addProperty(
                PropertySpec.builder(
                    "resString",
                    Int::class,
                    KModifier.INTERNAL
                ).initializer("resString").build()
            )
            .apply {
                symbol.getSealedSubclasses()
                    .filterNot { it.isAnnotationPresent(Ignore::class) }
                    .forEach { subclass ->
                        addEnumConstant(subclass)
                    }
            }
            .build()
    }

    @OptIn(KspExperimental::class)
    private fun TypeSpec.Builder.addEnumConstant(subclass: KSClassDeclaration) {
        val subClassName = subclass.simpleName.getShortName()
        val annotationResString =
            subclass.getAnnotation<VaultEntity>()
                ?.resString
                ?: throw NotFoundException(
                    ErrorMessages.VAULT_ENTITY_ANNOTATION_MISSING.format(
                        subClassName
                    )
                )

        addEnumConstant(
            subClassName,
            TypeSpec.anonymousClassBuilder()
                .addSuperclassConstructorParameter(
                    "%M",
                    stringRes(annotationResString)
                )
                .build()
        )
    }

    private fun createGetStringFunction(className: com.squareup.kotlinpoet.ClassName): FunSpec {
        return FunSpec.builder("getString")
            .addAnnotation(COMPOSABLE_CLASS_NAME)
            .receiver(className)
            .returns(String::class)
            .addStatement("return %M(resString)", STRING_RESOURCE_MEMBER_NAME)
            .build()
    }
}

package de.davis.keygo.automation.processor.kotlinpoet

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.ksp.writeTo

@KotlinPoetDsl
class FileBuilder(
    private val codeGenerator: CodeGenerator,
    val fileSpecBuilder: FileSpec.Builder,
) : FunctionHolder<FileSpec.Builder>(fileSpecBuilder) {

    fun build() {
        fileSpecBuilder
            .build()
            .writeTo(codeGenerator, Dependencies.ALL_FILES)
    }
}

@KotlinPoetDsl
fun file(
    codeGenerator: CodeGenerator,
    className: ClassName,
    builder: FileBuilder.(ClassName) -> Unit
) {
    FileBuilder(codeGenerator, FileSpec.builder(className))
        .apply {
            builder(className)
        }
        .build()
}
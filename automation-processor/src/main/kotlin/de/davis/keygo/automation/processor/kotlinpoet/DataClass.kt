package de.davis.keygo.automation.processor.kotlinpoet

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.TypeSpec

@KotlinPoetDsl
class DataClassBuilder(
    name: ClassName,
    private val dataClassBuilder: TypeSpec.Builder = TypeSpec.classBuilder(name)
        .addModifiers(KModifier.DATA, KModifier.INTERNAL)
) : Constructable(dataClassBuilder) {

    fun annotation(annotationSpec: AnnotationSpec) {
        dataClassBuilder.addAnnotation(annotationSpec)
    }

    fun build() = dataClassBuilder.build()
}

@KotlinPoetDsl
fun FileBuilder.dataClass(name: ClassName, builder: DataClassBuilder.() -> Unit) {
    val dataClassBuilder = DataClassBuilder(name)
    dataClassBuilder.builder()
    fileSpecBuilder.addType(dataClassBuilder.build())
}
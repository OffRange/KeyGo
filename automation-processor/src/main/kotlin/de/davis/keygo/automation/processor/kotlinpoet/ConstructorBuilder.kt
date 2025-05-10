package de.davis.keygo.automation.processor.kotlinpoet

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import kotlin.reflect.KClass

@KotlinPoetDsl
class ConstructorBuilder {

    val constructorBuilder = FunSpec.constructorBuilder()
    val parameters = mutableListOf<PropertySpec>()

    fun parameter(name: String, type: KClass<*>, vararg modifiers: KModifier) {
        constructorBuilder.addParameter(name, type)
        parameters.add(
            PropertySpec.builder(name, type, *modifiers)
                .initializer(name)
                .build()
        )
    }

    fun parameter(
        name: String,
        type: TypeName,
        annotations: List<AnnotationSpec> = emptyList(),
        vararg modifiers: KModifier,
    ) {
        val parameter = ParameterSpec.builder(name, type)
            .apply {
                annotations.forEach { addAnnotation(it) }
            }
            .build()

        constructorBuilder.addParameter(parameter)
        parameters.add(
            PropertySpec.builder(name, type, *modifiers)
                .initializer(name)
                .build()
        )
    }
}

open class Constructable(private val typeSpecBuilder: TypeSpec.Builder) {

    fun constructor(builder: ConstructorBuilder.() -> Unit) {
        val constructorBuilder = ConstructorBuilder()
        constructorBuilder.builder()
        typeSpecBuilder.primaryConstructor(constructorBuilder.constructorBuilder.build())
            .apply {
                constructorBuilder.parameters.forEach { parameter ->
                    typeSpecBuilder.addProperty(parameter)
                }
            }
    }
}
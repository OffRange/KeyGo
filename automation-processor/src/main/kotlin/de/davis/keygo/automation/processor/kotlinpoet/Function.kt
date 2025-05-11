package de.davis.keygo.automation.processor.kotlinpoet

import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.MemberSpecHolder
import com.squareup.kotlinpoet.TypeName

@KotlinPoetDsl
class FunctionBuilder(
    private val functionBuilder: FunSpec.Builder
) {

    fun receiver(receiver: TypeName) {
        functionBuilder.receiver(receiver)
    }

    fun returns(returns: TypeName) {
        functionBuilder.returns(returns)
    }

    fun parameter(
        name: String,
        type: TypeName,
        vararg modifiers: KModifier
    ) {
        functionBuilder.addParameter(name, type, *modifiers)
    }

    fun `return`(format: String, vararg args: Any) {
        functionBuilder.addStatement("return $format", *args)
    }

    fun build() = functionBuilder.build()
}

open class FunctionHolder<T : MemberSpecHolder.Builder<T>>(
    private val memberSpecHolderBuilder: MemberSpecHolder.Builder<T>
) {

    fun mapperFunction(
        name: String,
        receiver: TypeName,
        returnType: TypeName,
        block: FunctionBuilder.() -> Unit
    ) {
        val funSpecBuilder = FunSpec.builder(name)
            .addModifiers(KModifier.INTERNAL)

        val functionBuilder = FunctionBuilder(funSpecBuilder)
        functionBuilder.receiver(receiver)
        functionBuilder.returns(returnType)
        functionBuilder.block()
        memberSpecHolderBuilder.addFunction(functionBuilder.build())
    }
}
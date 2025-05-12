package de.davis.keygo.automation.processor.kotlinpoet

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.TypeSpec

@KotlinPoetDsl
class EnumBuilder(
    name: ClassName,
    private val enumBuilder: TypeSpec.Builder = TypeSpec.enumBuilder(name)
) : Constructable(enumBuilder) {

    fun entry(name: String, builder: EntryBuilder.() -> Unit = {}) {
        enumBuilder.addEnumConstant(name, EntryBuilder().apply(builder).typeSpec.build())
    }

    fun build(): TypeSpec = enumBuilder.build()

    class EntryBuilder {

        val typeSpec = TypeSpec.anonymousClassBuilder()

        fun parameter(memberName: MemberName) {
            typeSpec.addSuperclassConstructorParameter("%L", memberName)
        }

        fun parameter(format: String, vararg args: Any) {
            typeSpec.addSuperclassConstructorParameter(format, *args)
        }
    }
}

@KotlinPoetDsl
fun FileBuilder.enum(name: ClassName, builder: EnumBuilder.() -> Unit) {
    val enumBuilder = EnumBuilder(name)
    enumBuilder.builder()
    fileSpecBuilder.addType(enumBuilder.build())
}
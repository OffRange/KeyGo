package de.davis.keygo.automation.processor.model

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.ksp.toTypeName
import de.davis.keygo.automation.processor.ext.getAnnotation
import de.davis.keygo.processor.annotation.Id

data class Property(
    val name: String,
    val type: TypeName,
    val isId: Boolean
)

fun KSClassDeclaration.getOwnProperties(): Sequence<Property> {
    return getAllProperties()
        .filter { it.findOverridee() == null }
        .map {
            Property(
                name = it.simpleName.asString(),
                type = it.type.resolve().toTypeName(),
                isId = it.getAnnotation<Id>() != null
            )
        }
}
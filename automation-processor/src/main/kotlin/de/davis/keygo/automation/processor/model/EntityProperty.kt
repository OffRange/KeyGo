package de.davis.keygo.automation.processor.model

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.TypeName

data class EntityProperty(
    val name: String,
    val type: TypeName,
    val annotation: AnnotationSpec? = null
)

package de.davis.keygo.automation.processor.model

import com.squareup.kotlinpoet.AnnotationSpec
import de.davis.keygo.automation.processor.util.INDEX_CLASS_NAME

data class Index(
    val value: List<String>,
    val unique: Boolean = false,
) {
    fun index() =
        AnnotationSpec.Companion.builder(INDEX_CLASS_NAME)
            .addMember("value = [%S]", value.joinToString(", "))
            .addMember("unique = %L", unique)
            .build()
}
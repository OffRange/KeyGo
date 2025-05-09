package de.davis.keygo.automation.processor.model

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import de.davis.keygo.automation.processor.util.FOREIGN_KEY_CLASS_NAME

data class ForeignKey(
    val entity: ClassName,
    val parentColumns: List<String>,
    val childColumns: List<String>,
    val deferred: Boolean = false,
) {
    fun foreignKey() =
        AnnotationSpec.Companion.builder(FOREIGN_KEY_CLASS_NAME)
            .addMember("entity = %T::class", entity)
            .addMember("parentColumns = [%S]", parentColumns.joinToString(", "))
            .addMember("childColumns = [%S]", childColumns.joinToString(", "))
            .addMember("onDelete = %T.%L", FOREIGN_KEY_CLASS_NAME, "CASCADE")
            .apply { if (deferred) addMember("deferred = true") }
            .build()
}
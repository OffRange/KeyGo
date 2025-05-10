package de.davis.keygo.automation.processor.util

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.MemberName
import de.davis.keygo.automation.processor.model.ForeignKey
import de.davis.keygo.automation.processor.model.Index
import de.davis.keygo.automation.processor.model.Options

class GetClassName(options: Options) {
    private val packageName =
        options.getOrDefault(Options.KEY_GENERATED_AUTOMATION_PACKAGE, "generated")

    operator fun invoke(
        simpleName: String,
        packageNameSuffix: String = "",
    ) = packageNameSuffix.let {
        if (it.isNotEmpty()) {
            ClassName(
                "$packageName.$it", simpleName
            )
        } else {
            ClassName(packageName, simpleName)
        }
    }
}

fun stringRes(name: String) = MemberName("de.davis.keygo.R.string", name)

fun primaryRoomKey(autoGenerate: Boolean = true) =
    AnnotationSpec.builder(ClassName("androidx.room", "PrimaryKey")).apply {
        if (autoGenerate) addMember("autoGenerate = true")
    }.build()

fun roomColumnInfo(name: String) =
    AnnotationSpec.builder(ClassName("androidx.room", "ColumnInfo"))
        .addMember("name = %S", name)
        .build()

fun roomEntity(foreignKey: ForeignKey? = null, index: Index? = null) =
    AnnotationSpec.builder(ENTITY_CLASS_NAME)
        .apply {
            foreignKey?.let { addMember("foreignKeys = [%L]", it.foreignKey()) }
            index?.let { addMember("indices = [%L]", it.index()) }
        }
        .build()

val FOREIGN_KEY_CLASS_NAME = ClassName("androidx.room", "ForeignKey")
val INDEX_CLASS_NAME = ClassName("androidx.room", "Index")
val ENTITY_CLASS_NAME = ClassName("androidx.room", "Entity")
val COMPOSABLE_CLASS_NAME = ClassName("androidx.compose.runtime", "Composable")
val STRING_RESOURCE_MEMBER_NAME = MemberName("androidx.compose.ui.res", "stringResource")
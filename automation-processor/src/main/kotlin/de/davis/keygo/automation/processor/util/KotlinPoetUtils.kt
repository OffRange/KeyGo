package de.davis.keygo.automation.processor.util

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.MemberName
import de.davis.keygo.automation.processor.model.Options

class GetClassName(options: Options) {

    private val androidNamespace = options.require(Options.KEY_AUTOMATION_ANDROID_NAMESPACE)

    private val packageNamePrefix =
        options.getOrDefault(Options.KEY_AUTOMATION_ANDROID_NAMESPACE_SUFFIX, "generated")

    operator fun invoke(packageName: String, simpleName: String): ClassName {
        val packageNameWithoutNamespace = packageName.removePrefix(androidNamespace)
        val finalPackageName = listOf(
            androidNamespace,
            packageNamePrefix,
            packageNameWithoutNamespace
        ).joinToString(".").trimEnd('.')

        return ClassName(finalPackageName, simpleName)
    }
}

class GetStringRes(options: Options) {

    private val androidNamespace = options.require(Options.KEY_AUTOMATION_ANDROID_NAMESPACE)

    operator fun invoke(name: String): MemberName {
        return MemberName("$androidNamespace.R.string", name)
    }
}


fun defaultIconMemberName(iconName: String) =
    MemberName("${ICONS_DEFAULT_CLASS_NAME.packageName}.filled", iconName)

val COMPOSABLE_CLASS_NAME = ClassName("androidx.compose.runtime", "Composable")
val STRING_RESOURCE_MEMBER_NAME = MemberName("androidx.compose.ui.res", "stringResource")

val ICONS_DEFAULT_CLASS_NAME = ClassName("androidx.compose.material.icons", "Icons")
val IMAGE_VECTOR_CLASS_NAME = ClassName("androidx.compose.ui.graphics.vector", "ImageVector")
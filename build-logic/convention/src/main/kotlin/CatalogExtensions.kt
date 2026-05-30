import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependencyBundle
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.DependencyHandlerScope
import org.gradle.kotlin.dsl.getByType

internal val Project.catalog: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal class VersionCatalogGetter<T>(
    private val getter: (String) -> T,
) {
    operator fun get(value: String): T = getter(value)
}

internal val VersionCatalog.version: VersionCatalogGetter<Int>
    get() = VersionCatalogGetter {
        findVersion(it).get().requiredVersion.toInt()
    }

internal val VersionCatalog.plugin: VersionCatalogGetter<String>
    get() = VersionCatalogGetter {
        findPlugin(it).get().get().pluginId
    }

internal val VersionCatalog.library: VersionCatalogGetter<Provider<MinimalExternalModuleDependency>>
    get() = VersionCatalogGetter {
        findLibrary(it).get()
    }

internal val VersionCatalog.bundle: VersionCatalogGetter<Provider<ExternalModuleDependencyBundle>>
    get() = VersionCatalogGetter {
        findBundle(it).get()
    }

internal fun DependencyHandlerScope.implementation(dependency: Any) {
    add("implementation", dependency)
}

internal fun DependencyHandlerScope.testImplementation(dependency: Any) {
    add("testImplementation", dependency)
}

internal fun DependencyHandlerScope.debugImplementation(dependency: Any) {
    add("debugImplementation", dependency)
}

internal fun DependencyHandlerScope.androidTestImplementation(dependency: Any) {
    add("androidTestImplementation", dependency)
}

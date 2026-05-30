import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class AndroidComposeConventionPlugin : Plugin<Project> {

    override fun apply(target: Project) = with(target) {
        with(pluginManager) {
            apply("keygo.android.library")
            apply(catalog.plugin["kotlin-compose"])
        }

        dependencies {
            implementation(platform(catalog.library["androidx-compose-bom"]))
            implementation(catalog.bundle["android-compose"])
            implementation(catalog.library["koin-androidx-compose"])
            debugImplementation(catalog.library["androidx-ui-tooling"])
            debugImplementation(catalog.library["androidx-ui-test-manifest"])
        }
    }
}

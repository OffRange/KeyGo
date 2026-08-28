import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

class AndroidLibraryConventionPlugin : Plugin<Project> {

    override fun apply(target: Project) = with(target) {
        with(pluginManager) {
            apply(catalog.plugin["android-library"])
            apply(catalog.plugin["koin-compiler"])
        }

        extensions.configure<LibraryExtension> {
            compileSdk = catalog.version["compileSdk"]

            defaultConfig {
                minSdk = catalog.version["minSdk"]

                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                consumerProguardFiles("consumer-rules.pro")
            }

            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
        }

        extensions.configure<KotlinAndroidProjectExtension> {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_17)
            }
        }

        dependencies {
            implementation(platform(catalog.library["koin-bom"]))
            implementation(catalog.bundle["koin-core"])
            testImplementation(catalog.bundle["test-common"])
            androidTestImplementation(catalog.library["androidx-junit"])
            androidTestImplementation(catalog.library["androidx-espresso-core"])
        }
    }
}

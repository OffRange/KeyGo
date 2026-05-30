import com.google.protobuf.gradle.ProtobufExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class AndroidProtobufConventionPlugin : Plugin<Project> {

    override fun apply(target: Project) = with(target) {
        pluginManager.apply(catalog.plugin["google-protobuf"])

        dependencies {
            implementation(catalog.library["google-protobuf-kotlin-lite"])
        }

        extensions.configure<ProtobufExtension> {
            protoc {
                artifact = catalog.library["google-protobuf-protoc"].get().toString()
            }

            generateProtoTasks {
                all().forEach { task ->
                    if (task.name.contains("TestFixtures", ignoreCase = true)) {
                        task.enabled = false
                        return@forEach
                    }
                    task.builtins {
                        create("java") { option("lite") }
                        create("kotlin") { option("lite") }
                    }
                }
            }
        }
    }
}

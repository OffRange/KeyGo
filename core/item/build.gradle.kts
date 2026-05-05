import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.google.protobuf)
    alias(libs.plugins.androidx.room)
    alias(libs.plugins.google.ksp)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.koin.compiler)
}

android {
    namespace = "de.davis.keygo.core.item"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Required for the generated composable functions
    buildFeatures {
        compose = true
    }

    testFixtures {
        enable = true
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-parameters")
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(projects.automation)
    ksp(projects.automationProcessor)

    api(projects.core.util)

    implementation(libs.gosimple.nbvcxz)

    // Jetpack Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.material3)

    // Datastore
    implementation(libs.androidx.datastore)
    implementation(libs.google.protobuf.kotlin.lite)

    // Koin DI
    implementation(project.dependencies.platform(libs.koin.bom))
    implementation(libs.koin.androidx.compose)
    implementation(libs.koin.annotations)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.io.mockk)

    testFixturesImplementation(libs.kotlinx.coroutines.core)
    testFixturesApi(projects.core.util)
    testFixturesImplementation(projects.core.security)
    testFixturesImplementation(projects.rust)
    testFixturesImplementation(project.dependencies.platform(libs.androidx.compose.bom))
    testFixturesImplementation(libs.androidx.compose.runtime) {
        because("https://issuetracker.google.com/issues/259523353#comment32")
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

ksp {
    arg("automation.android_namespace", "de.davis.keygo.core.item")
}

protobuf {
    protoc {
        artifact = libs.google.protobuf.protoc.get().toString()
    }

    generateProtoTasks {
        all().forEach { task ->
            if (task.name.contains("TestFixtures", ignoreCase = true)) {
                task.enabled = false
                return@forEach
            }
            task.builtins {
                create("java") {
                    option("lite")
                }
                create("kotlin") {
                    option("lite")
                }
            }
        }
    }
}

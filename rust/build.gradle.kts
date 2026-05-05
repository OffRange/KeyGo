import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.koin.compiler)
}

android {
    namespace = "de.davis.keygo.rust"
    compileSdk {
        version = release(libs.versions.compileSdk.get().toInt())
    }

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        ndk {
            abiFilters += setOf("armeabi-v7a", "arm64-v8a", "x86_64")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Ensure UniFFI-generated Kotlin sources are compiled. The UniFFI Makefile
    // outputs Kotlin into build/generated/source/uniffi/, which isn't a
    // conventional AGP-generated directory. Register it as a source dir so
    // the Kotlin/Java compiler picks up the generated bindings.
    sourceSets {
        getByName("main") {
            jniLibs.directories += "build/generated/source/uniffi/jniLibs"
            kotlin.directories += "build/generated/source/uniffi/kotlin"
        }
    }

    testFixtures {
        enable = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}


dependencies {
    implementation(libs.androidx.core.ktx)

    api(projects.core.util)

    // Koin DI
    implementation(project.dependencies.platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.annotations)

    implementation("net.java.dev.jna:jna:5.18.1@aar")
    // kotlinx-coroutines-core is also required for async uniffi functions:
    implementation(libs.kotlinx.coroutines.core)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

val buildRust by tasks.register<Exec>("buildRust") {
    group = "build"
    description = "Build Rust library"

    onlyIf {
        providers.gradleProperty("rust.compile").getOrElse("true").toBoolean()
    }

    workingDir = projectDir.resolve("rust-code")

    commandLine(
        "make",
        "all",
        "MIN_SDK=${libs.versions.minSdk.get()}",
    )
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(buildRust)
}
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
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
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}


dependencies {
    implementation(libs.androidx.core.ktx)

    api(projects.core.util)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

val buildRust by tasks.register<Exec>("buildRust") {
    group = "build"
    description = "Build Rust library"

    onlyIf {
        properties["buildRust"].toString().toBoolean()
    }

    workingDir = projectDir.resolve("rust-code")

    commandLine(
        "./build.sh",
        "--min-platform",
        libs.versions.minSdk.get()
    )
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(buildRust)
}
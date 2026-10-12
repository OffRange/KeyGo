plugins {
    alias(libs.plugins.keygo.android.library)
}

val uniffiDir = "build/generated/source/uniffi"

android {
    namespace = "de.davis.keygo.rust"

    defaultConfig {
        ndk {
            abiFilters += setOf("armeabi-v7a", "arm64-v8a", "x86_64")
        }
    }

    sourceSets {
        getByName("main") {
            jniLibs.directories += "$uniffiDir/jniLibs"
            kotlin.directories += "$uniffiDir/kotlin"
        }
    }

    testFixtures {
        enable = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)

    api(projects.core.util)

    implementation(libs.jna) {
        artifact {
            type = "aar"
        }
    }
    implementation(libs.kotlinx.coroutines.core)

}

val buildRust by tasks.register<Exec>("buildRust") {
    group = "build"
    description = "Build Rust library"

    onlyIf {
        providers.gradleProperty("rust.compile").getOrElse("true").toBoolean()
    }

    workingDir = rootDir.resolve("rust")

    commandLine(
        "make",
        "all",
        "MIN_SDK=${libs.versions.minSdk.get()}",
        "JNI_DIR=${projectDir.resolve("$uniffiDir/jniLibs")}",
        "KOTLIN_OUT=${projectDir.resolve("$uniffiDir/kotlin")}",
    )
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(buildRust)
}

plugins {
    alias(libs.plugins.keygo.android.library)
}

android {
    namespace = "de.davis.keygo.rust"

    defaultConfig {
        ndk {
            abiFilters += setOf("armeabi-v7a", "arm64-v8a", "x86_64")
        }
    }

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

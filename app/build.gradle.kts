import de.davis.gradle.plugin.versioning.versionedBy
import io.github.z4kn4fein.semver.Version
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.koin.compiler)
    alias(libs.plugins.git.semantic.versioning)
    alias(libs.plugins.google.protobuf)

    alias(libs.plugins.mikepenz.aboutlibraries)
}

versioning {
    minVersion = Version(major = 2, minor = 1)
}

android versionedBy versioning
android {
    namespace = "de.davis.keygo"
    compileSdk = libs.versions.compileSdk.get().toInt()

    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("debug.keystore")
            storePassword = "android"
            keyAlias = "debug"
            keyPassword = "android"
        }

        // Release credentials come from the environment, never from -P properties:
        // project properties end up in the Gradle process argv, which stays readable
        // by every process on the build machine for the whole build. Without the env
        // vars no release config is created and release stays unsigned, so local and
        // F-Droid builds behave exactly as before.
        System.getenv("KEYSTORE_FILE")?.takeIf { it.isNotBlank() }?.let { keystore ->
            create("release") {
                storeFile = file(keystore)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    defaultConfig {
        applicationId = "de.davis.passwordmanager"
        minSdk = 26
        targetSdk = libs.versions.compileSdk.get().toInt()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")

            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            // Packages symbols for the :rust cdylibs into the AAB so Play can
            // symbolicate native crashes. Stripped before delivery to devices.
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
            }
        }

        debug {
            applicationIdSuffix = ".debug"
        }

        create("staging") {
            initWith(getByName("release"))

            applicationIdSuffix = ".staging"
            versionNameSuffix = "-staging"

            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }

    flavorDimensions += listOf("store")
    productFlavors {
        create("playStore") {
            dimension = "store"
            isDefault = true
        }

        create("fdroid") {
            dimension = "store"
            versionNameSuffix = "-fdroid"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    // Koin DI
    implementation(project.dependencies.platform(libs.koin.bom))
    implementation(libs.koin.androidx.compose)
    implementation(libs.koin.annotations)
    implementation(libs.koin.androidx.workmanager)

    implementation(libs.aboutlibraries.compose.m3)

    implementation(projects.rust)
    implementation(projects.core.item)
    implementation(projects.core.identity)
    implementation(projects.core.ui)
    implementation(projects.feature.auth)
    implementation(projects.feature.listScreen)
    implementation(projects.feature.credentials)
    implementation(projects.feature.item.core)
    implementation(projects.feature.item.create)
    implementation(projects.feature.item.view)
    implementation(projects.feature.vault)
    implementation(projects.feature.totp)
    implementation(projects.feature.creditCard)
    implementation(projects.feature.autofill)
    implementation(projects.feature.settings)
    implementation(projects.feature.backup)
    implementation(projects.feature.onboarding)
    implementation(projects.legacyMigration)

    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material3.adaptive.navigation)
    implementation(libs.androidx.material3.adaptive.navigation.suite)
    implementation(libs.androidx.navigation.compose)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.androidx.navigation.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.junit)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

protobuf {
    protoc {
        artifact = libs.google.protobuf.protoc.get().toString()
    }

    generateProtoTasks {
        all().forEach { task ->
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

composeCompiler {
    reportsDestination = layout.buildDirectory.dir("compose_compiler")
    metricsDestination = layout.buildDirectory.dir("compose_compiler")
}

koinCompiler {
    userLogs = true
}
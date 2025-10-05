import de.davis.gradle.plugin.versioning.versionedBy
import io.github.z4kn4fein.semver.Version

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.git.semantic.versioning)
    alias(libs.plugins.google.ksp)
    alias(libs.plugins.google.protobuf)
}

versioning {
    minVersion = Version(major = 2)
}

android versionedBy versioning
android {
    namespace = "de.davis.keygo"
    compileSdk = 36

    defaultConfig {
        applicationId = "de.davis.passwordmanager.v2_new" // TODO: Change the applicationId
        minSdk = 26
        targetSdk = 36

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions {
        jvmTarget = "21"
    }
    buildFeatures {
        compose = true
    }

    flavorDimensions += listOf("store")
    productFlavors {
        create("playStore") {
            dimension = "store"
        }

        create("fdroid") {
            dimension = "store"
            versionNameSuffix = "-fdroid"
        }
    }
}

kotlin {
    compilerOptions {

        freeCompilerArgs.add("-Xcontext-parameters")
    }
}

dependencies {
    implementation(libs.okhttp)

    // Koin DI
    implementation(project.dependencies.platform(libs.koin.bom))
    implementation(project.dependencies.platform(libs.koin.annotations.bom))
    implementation(libs.koin.androidx.compose)
    implementation(libs.koin.annotations)
    ksp(libs.koin.ksp.compiler)

    implementation(projects.core.item)
    implementation(projects.core.util)

    // Datastore
    implementation(libs.androidx.datastore)
    implementation(libs.google.protobuf.kotlin.lite)

    implementation(libs.gosimple.nbvcxz)
    implementation(libs.turingcomplete.totp)

    implementation(libs.offrange.passgen)
    implementation(libs.argon2kt)

    implementation(libs.androidx.autofill)

    // Use GMS ML Kit for barcode scanning on the Play Store to minimize app size; use ZXing for F-Droid builds.
    "playStoreImplementation"(libs.gms.mlkit.barcode.scanning)
    "fdroidImplementation"(libs.zxing.barcode.scanning)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.compose)
    implementation(libs.androidx.camera.lifecycle)

    implementation(projects.migrationCreateAccess)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material3.adaptive.navigation)
    implementation(libs.androidx.material3.adaptive.navigation.suite)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.collections.immutable)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.io.mockk)
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
plugins {
    id("com.android.application")
    id("com.google.android.gms.oss-licenses-plugin")
    kotlin("android") // org.jetbrains.kotlin.android
    kotlin("plugin.parcelize")
    id("com.google.devtools.ksp") version "1.9.22-1.0.17"
    id("androidx.room") version "2.6.0"
}

android {
    namespace = "de.davis.passwordmanager"
    compileSdk = 34

    val runTasks = gradle.startParameter.taskNames
    val isDebugBuild = runTasks.any { it.contains("assembleDebug") }
    val build = 64 // alpha 0-31 | beta 32-63 | rc 64-95 | stable 96-99
    val major = 1
    val minor = 2
    val patch = 0

    // xxyyyzzbb
    val vCode = if (isDebugBuild) 10 else major * 10_000_000 + minor * 10_000 + patch * 100 + build

    val vName = "$major.$minor.$patch-${
        when (build.floorDiv(32)) {
            3 -> "" // build >= 96
            2 -> "rc" // build >= 64
            1 -> "beta" // build >= 32
            else -> "alpha"
        }
    }${String.format("%02d", (build % 32) + 1)}"

    defaultConfig {
        applicationId = "de.davis.passwordmanager"
        minSdk = 23
        targetSdk = 34
        versionCode = vCode
        versionName = vName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            isMinifyEnabled = true
        }

        debug {
            versionNameSuffix = "-debug"
            applicationIdSuffix = ".debug"
        }

        create("dev") {
            initWith(getByName("release"))

            versionNameSuffix = "-dev"
            applicationIdSuffix = ".dev"
        }
    }

    flavorDimensions += "market"
    productFlavors {
        create("playstore") {
            dimension = "market"

        }

        create("github") {
            dimension = "market"
            versionNameSuffix = "-github"
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    sourceSets {
        // Adds exported schema location as test app assets.
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")

    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.autofill:autofill:1.1.0")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    implementation("com.google.android.gms:play-services-oss-licenses:17.0.1")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.recyclerview:recyclerview-selection:1.1.0")
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.6")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.6")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.browser:browser:1.7.0")
    implementation("me.gosimple:nbvcxz:1.5.1")
    implementation("at.favre.lib:bcrypt:0.10.2")
    implementation("com.github.alvinhkh:TextDrawable:558677ea31")
    implementation("com.github.devnied.emvnfccard:library:3.0.1")
    implementation("net.grey-panther:natural-comparator:1.1")

    implementation("androidx.datastore:datastore:1.0.0")

    "githubImplementation"("com.squareup.retrofit2:retrofit:2.9.0")
    "githubImplementation"("org.kohsuke:github-api:1.318")

    implementation("com.opencsv:opencsv:5.9")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    androidTestImplementation("androidx.room:room-testing:2.6.1")

    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}

room {
    schemaDirectory("$projectDir/schemas")
}

ksp {
    arg("room.generateKotlin", "true")
}
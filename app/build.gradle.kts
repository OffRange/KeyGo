plugins {
    id("com.android.application")
    id("com.google.android.gms.oss-licenses-plugin")
    kotlin("android") // org.jetbrains.kotlin.android
    kotlin("plugin.parcelize")
    id("com.google.devtools.ksp") version "2.2.10-2.0.2"
    id("androidx.room") version "2.7.2"
    id("com.google.protobuf") version "0.9.5"
}

android {
    namespace = "de.davis.passwordmanager"
    compileSdk = 36

    val runTasks = gradle.startParameter.taskNames
    val isDebugBuild = runTasks.any { it.contains("assembleDebug") }
    val build = 96 // alpha 0-31 | beta 32-63 | rc 64-95 | stable 96-99
    val major = 1
    val minor = 2
    val patch = 1

    // xxyyyzzbb
    val vCode = if (isDebugBuild) 10 else major * 10_000_000 + minor * 10_000 + patch * 100 + build

    fun createSuffix(suffix: String): String = if (suffix.isNotBlank())
        "-${suffix}${String.format("%02d", (build % 32) + 1)}" else ""

    val vName = "$major.$minor.$patch${
        when (build.floorDiv(32)) {
            3 -> createSuffix("") // build >= 96
            2 -> createSuffix("-rc") // build >= 64
            1 -> createSuffix("beta") // build >= 32
            else -> createSuffix("alpha")
        }
    }"
    println("Computed version name: $vName")

    defaultConfig {
        applicationId = "de.davis.passwordmanager"
        minSdk = 23
        targetSdk = 36
        versionCode = vCode
        versionName = vName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("debug.keystore")
            storePassword = "android"
            keyAlias = "debug"
            keyPassword = "android"
        }
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
            signingConfig = signingConfigs.getByName("debug")
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
    implementation("androidx.core:core-ktx:1.17.0")

    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.autofill:autofill:1.3.0")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    implementation("com.google.android.gms:play-services-oss-licenses:17.2.2")
    implementation("com.google.code.gson:gson:2.13.1")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.recyclerview:recyclerview-selection:1.2.0")
    implementation("androidx.navigation:navigation-fragment-ktx:2.9.3")
    implementation("androidx.navigation:navigation-ui-ktx:2.9.3")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.browser:browser:1.9.0")
    implementation("me.gosimple:nbvcxz:1.5.1")
    implementation("at.favre.lib:bcrypt:0.10.2")
    implementation("com.github.alvinhkh:TextDrawable:558677ea31")
    implementation("com.github.devnied.emvnfccard:library:3.0.1")
    implementation("net.grey-panther:natural-comparator:1.1")

    implementation("com.github.keemobile:kotpass:0.6.1")
    implementation("com.squareup.okio:okio:3.16.0") //Needed for Meta for kotpass

    implementation("androidx.datastore:datastore:1.1.7")
    implementation("com.google.protobuf:protobuf-kotlin-lite:4.32.0")

    "githubImplementation"("com.squareup.retrofit2:retrofit:3.0.0")
    "githubImplementation"("org.kohsuke:github-api:1.329")

    implementation("com.opencsv:opencsv:5.12.0")

    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    ksp("androidx.room:room-compiler:2.7.2")
    androidTestImplementation("androidx.room:room-testing:2.7.2")

    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
}

room {
    schemaDirectory("$projectDir/schemas")
}

ksp {
    arg("room.generateKotlin", "true")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.25.2"
    }
    generateProtoTasks {
        all().forEach {
            it.builtins {
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
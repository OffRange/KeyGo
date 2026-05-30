plugins {
    `kotlin-dsl`
}

group = "de.davis.keygo.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.protobuf.gradlePlugin)
    implementation(libs.koin.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidlibrary") {
            id = "keygo.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidcompose") {
            id = "keygo.android.compose"
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("androidprotobuf") {
            id = "keygo.android.protobuf"
            implementationClass = "AndroidProtobufConventionPlugin"
        }
        register("kotlinjvm") {
            id = "keygo.kotlin.jvm"
            implementationClass = "KotlinJvmConventionPlugin"
        }
    }
}

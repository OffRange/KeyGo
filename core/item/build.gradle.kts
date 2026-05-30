plugins {
    alias(libs.plugins.keygo.android.compose)
    alias(libs.plugins.keygo.android.protobuf)
    alias(libs.plugins.androidx.room)
    alias(libs.plugins.google.ksp)
}

android {
    namespace = "de.davis.keygo.core.item"

    buildFeatures {
        compose = true
    }

    testFixtures {
        enable = true
    }
}

dependencies {
    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(projects.automation)
    ksp(projects.automationProcessor)

    api(projects.core.util)
    implementation(libs.gosimple.nbvcxz)

    implementation(libs.androidx.datastore)

    testImplementation(libs.io.mockk)
    testImplementation(libs.androidx.sqlite.bundled)

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

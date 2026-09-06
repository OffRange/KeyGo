plugins {
    alias(libs.plugins.keygo.android.compose)
    alias(libs.plugins.keygo.android.protobuf)
}

android {
    namespace = "de.davis.keygo.core.security"

    testFixtures {
        enable = true
    }
}

dependencies {
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.lifecycle.process)

    implementation(projects.core.item)
    api(projects.core.util)
    api(projects.rust)

    testImplementation(libs.robolectric)
    testImplementation(testFixtures(projects.rust))
    testImplementation(testFixtures(projects.core.item))
    testImplementation(testFixtures(projects.core.util))

    testFixturesApi(projects.core.item)
    testFixturesApi(projects.rust)
    testFixturesImplementation(libs.kotlinx.coroutines.core)
    testFixturesImplementation(testFixtures(projects.rust))
    testFixturesImplementation(project.dependencies.platform(libs.androidx.compose.bom))
    testFixturesImplementation(libs.androidx.compose.runtime) {
        because("https://issuetracker.google.com/issues/259523353#comment32")
    }
}

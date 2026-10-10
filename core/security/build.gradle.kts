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
    implementation(libs.androidx.lifecycle.process)

    implementation(projects.core.item)
    api(projects.core.util)
    api(projects.core.rust)

    testImplementation(libs.robolectric)
    testImplementation(testFixtures(projects.core.rust))
    testImplementation(testFixtures(projects.core.item))
    testImplementation(testFixtures(projects.core.util))

    testFixturesApi(projects.core.item)
    testFixturesApi(projects.core.rust)
    testFixturesImplementation(libs.kotlinx.coroutines.core)
    testFixturesImplementation(testFixtures(projects.core.rust))
    testFixturesImplementation(project.dependencies.platform(libs.androidx.compose.bom))
    testFixturesImplementation(libs.androidx.compose.runtime) {
        because("https://issuetracker.google.com/issues/259523353#comment32")
    }
}

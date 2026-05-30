plugins {
    alias(libs.plugins.keygo.android.compose)
    alias(libs.plugins.keygo.android.protobuf)
}

android {
    namespace = "de.davis.keygo.core.identity"

    testFixtures {
        enable = true
    }
}

dependencies {
    implementation(projects.core.security)
    implementation(projects.core.item)
    implementation(projects.rust)

    // Datastore
    implementation(libs.androidx.datastore)

    testImplementation(libs.io.mockk)
    testImplementation(testFixtures(projects.core.item))
    testImplementation(testFixtures(projects.core.security))
    testImplementation(testFixtures(projects.rust))

    testFixturesApi(projects.core.util)
    testFixturesImplementation(projects.rust)
    testFixturesImplementation(project.dependencies.platform(libs.androidx.compose.bom))
    testFixturesImplementation(libs.androidx.compose.runtime) {
        because("https://issuetracker.google.com/issues/259523353#comment32")
    }
}

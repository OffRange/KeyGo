plugins {
    alias(libs.plugins.keygo.android.compose)
    alias(libs.plugins.keygo.android.protobuf)
}

android {
    namespace = "de.davis.keygo.core.sync"

    testFixtures {
        enable = true
    }
}

dependencies {
    api(projects.core.rust)

    implementation(projects.core.security)
    implementation(projects.core.identity)
    implementation(projects.core.ui)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.androidx.datastore)

    testImplementation(libs.okhttp.jvm)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(testFixtures(projects.core.rust))
    testImplementation(testFixtures(projects.core.security))

    testFixturesApi(projects.core.util)
    testFixturesImplementation(libs.kotlinx.coroutines.core)
    testFixturesImplementation(project.dependencies.platform(libs.androidx.compose.bom))
    testFixturesImplementation(libs.androidx.compose.runtime) {
        because("https://issuetracker.google.com/issues/259523353#comment32")
    }
}

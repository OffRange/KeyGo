plugins {
    alias(libs.plugins.keygo.android.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "de.davis.keygo.feature.settings"

    defaultConfig {
        missingDimensionStrategy("store", "playStore")
    }
}

dependencies {
    implementation(projects.core.ui)
    implementation(projects.core.identity)
    implementation(projects.core.item)
    implementation(projects.feature.autofill)

    implementation(libs.androidx.navigation.compose)

    testImplementation(testFixtures(projects.core.identity))
    testImplementation(testFixtures(projects.core.security))
    testImplementation(testFixtures(projects.rust))
}

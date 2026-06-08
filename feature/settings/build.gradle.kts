plugins {
    alias(libs.plugins.keygo.android.compose)
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

    testImplementation(testFixtures(projects.core.identity))
    testImplementation(testFixtures(projects.core.security))
    testImplementation(testFixtures(projects.rust))
}

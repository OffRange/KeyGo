plugins {
    alias(libs.plugins.keygo.android.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "de.davis.keygo.feature.credentials"

    defaultConfig {
        missingDimensionStrategy("store", "playStore")
    }
}

dependencies {
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.credentials)
    implementation(libs.kotlinx.serialization.json)

    implementation(projects.rust)
    implementation(projects.core.identity)
    implementation(projects.core.item)
    implementation(projects.core.ui)
    implementation(projects.feature.item.create)
    implementation(projects.feature.listScreen)
    implementation(projects.feature.auth)

    testImplementation(libs.io.mockk)
    testImplementation(testFixtures(projects.core.identity))
    testImplementation(testFixtures(projects.core.item))
    testImplementation(testFixtures(projects.core.security))
    testImplementation(testFixtures(projects.rust))

}

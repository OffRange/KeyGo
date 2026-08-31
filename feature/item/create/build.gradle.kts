plugins {
    alias(libs.plugins.keygo.android.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "de.davis.keygo.feature.item.create"

    defaultConfig {
        missingDimensionStrategy("store", "playStore")
    }
}

dependencies {
    implementation(libs.androidx.navigation3.runtime)

    implementation(projects.core.ui)
    implementation(projects.core.item)
    implementation(projects.core.security)
    implementation(projects.feature.item.core)
    implementation(projects.feature.listScreen)
    implementation(projects.feature.totp)
    implementation(projects.feature.creditCard)

    implementation(libs.offrange.passgen)

    testImplementation(testFixtures(projects.core.item))
    testImplementation(testFixtures(projects.core.security))
    testImplementation(testFixtures(projects.core.util))
    testImplementation(testFixtures(projects.rust))
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.junit)
}

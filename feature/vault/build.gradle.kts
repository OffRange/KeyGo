plugins {
    alias(libs.plugins.keygo.android.compose)
}

android {
    namespace = "de.davis.keygo.feature.vault"
}

dependencies {
    implementation(projects.core.item)
    implementation(projects.core.ui)
    implementation(projects.core.util)
    implementation(projects.core.security)
    implementation(projects.rust)

    testImplementation(testFixtures(projects.core.util))
    testImplementation(testFixtures(projects.core.item))
    testImplementation(testFixtures(projects.core.security))
    testImplementation(testFixtures(projects.rust))

}

plugins {
    alias(libs.plugins.keygo.android.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "de.davis.keygo.feature.list_screen"
}

dependencies {
    implementation(projects.core.item)
    implementation(projects.core.ui)
    implementation(projects.core.util)
    implementation(projects.core.security)
    implementation(projects.feature.vault)

    testImplementation(testFixtures(projects.core.item))

}

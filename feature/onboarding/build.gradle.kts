plugins {
    alias(libs.plugins.keygo.android.compose)
}

android {
    namespace = "de.davis.keygo.feature.onboarding"
}

dependencies {
    implementation(projects.core.ui)
    implementation(projects.core.item)
    implementation(projects.feature.backup)
}

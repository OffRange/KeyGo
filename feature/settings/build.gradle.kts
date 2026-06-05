plugins {
    alias(libs.plugins.keygo.android.compose)
}

android {
    namespace = "de.davis.keygo.feature.settings"
}

dependencies {
    implementation(projects.core.ui)
    implementation(projects.core.identity)
    implementation(projects.feature.autofill)
}

plugins {
    alias(libs.plugins.keygo.android.compose)
}

android {
    namespace = "de.davis.keygo.feature.settings"
}

dependencies {
    implementation(projects.core.ui)
}

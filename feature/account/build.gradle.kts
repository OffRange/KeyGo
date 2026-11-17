plugins {
    alias(libs.plugins.keygo.android.compose)
}

android {
    namespace = "de.davis.keygo.feature.account"
}

dependencies {
    implementation(projects.core.ui)
    implementation(projects.core.util)
}

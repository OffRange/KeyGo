plugins {
    alias(libs.plugins.keygo.android.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "de.davis.keygo.feature.password_health"
}

dependencies {
    implementation(projects.core.ui)
    implementation(projects.core.item)
    implementation(projects.feature.item.create)
}

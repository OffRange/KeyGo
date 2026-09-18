plugins {
    alias(libs.plugins.keygo.android.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "de.davis.keygo.feature.password_health"

    defaultConfig {
        missingDimensionStrategy("store", "playStore")
    }
}

dependencies {
    implementation(projects.core.ui)
    implementation(projects.core.item)
    implementation(projects.core.util)
    implementation(projects.core.security)
    implementation(projects.feature.item.create)
}

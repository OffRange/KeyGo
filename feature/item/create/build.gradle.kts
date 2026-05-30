plugins {
    alias(libs.plugins.keygo.android.compose)
}

android {
    namespace = "de.davis.keygo.feature.item.create"

    defaultConfig {
        missingDimensionStrategy("store", "playStore")
    }
}

dependencies {
    implementation(projects.core.ui)
    implementation(projects.core.item)
    implementation(projects.core.security)
    implementation(projects.feature.item.core)
    implementation(projects.feature.totp)
    implementation(projects.feature.creditCard)

    implementation(libs.offrange.passgen)

}

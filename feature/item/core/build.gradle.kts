plugins {
    alias(libs.plugins.keygo.android.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "de.davis.keygo.feature.item.core"

    testFixtures {
        enable = true
    }

    flavorDimensions += listOf("store")
    productFlavors {
        create("playStore") {
            dimension = "store"
        }

        create("fdroid") {
            dimension = "store"
        }
    }
}

dependencies {
    implementation(projects.core.item)
    implementation(projects.core.security)
    implementation(projects.core.ui)
    implementation(projects.feature.totp)

    testImplementation(testFixtures(projects.core.item))
    testImplementation(testFixtures(projects.core.security))
    testImplementation(testFixtures(projects.rust))

}

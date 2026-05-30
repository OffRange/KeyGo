plugins {
    alias(libs.plugins.keygo.android.compose)
}

android {
    namespace = "de.davis.keygo.feature.totp"

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
    implementation(libs.com.google.accompanist.permissions)

    implementation(projects.rust)
    implementation(projects.core.security)
    implementation(projects.core.item)
    implementation(projects.core.util)

    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.compose)
    implementation(libs.androidx.camera.lifecycle)

    "playStoreImplementation"(libs.gms.mlkit.barcode.scanning)
    "fdroidImplementation"(libs.zxing.barcode.scanning)

    testImplementation(testFixtures(projects.core.security))
    testImplementation(testFixtures(projects.core.item))
    testImplementation(testFixtures(projects.rust))
}

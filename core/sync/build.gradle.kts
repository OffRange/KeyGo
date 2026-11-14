plugins {
    alias(libs.plugins.keygo.android.library)
}

android {
    namespace = "de.davis.keygo.core.sync"
}

dependencies {
    api(projects.core.rust)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.okhttp.jvm)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(testFixtures(projects.core.rust))
}

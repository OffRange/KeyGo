plugins {
    alias(libs.plugins.keygo.android.library)
    alias(libs.plugins.keygo.android.protobuf)
}

android {
    namespace = "de.davis.keygo.core.sync"
}

dependencies {
    api(projects.core.rust)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.androidx.datastore)
    
    testImplementation(libs.okhttp.jvm)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(testFixtures(projects.core.rust))
}

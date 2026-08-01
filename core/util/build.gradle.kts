plugins {
    alias(libs.plugins.keygo.android.compose)
}

android {
    namespace = "de.davis.keygo.core.util"

    testFixtures {
        enable = true
    }
}

dependencies {
    implementation(libs.okhttp)

    api(libs.androidx.datastore)
    api(libs.google.protobuf.kotlin.lite)

    testImplementation(libs.okhttp.jvm)

    testFixturesImplementation(libs.kotlin.test)
    testFixturesImplementation(project.dependencies.platform(libs.androidx.compose.bom))
    testFixturesImplementation(libs.androidx.compose.runtime) {
        because("https://issuetracker.google.com/issues/259523353#comment32")
    }
}

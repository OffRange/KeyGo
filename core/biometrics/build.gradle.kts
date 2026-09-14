plugins {
    alias(libs.plugins.keygo.android.compose)
}

android {
    namespace = "de.davis.keygo.core.biometrics"

    testFixtures {
        enable = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    api(projects.core.security)
    implementation(libs.androidx.biometric)

    testImplementation(libs.robolectric)
    testImplementation(testFixtures(projects.core.security))
    testImplementation(testFixtures(projects.core.util))

    testFixturesApi(testFixtures(projects.core.security))
    testFixturesImplementation(libs.kotlinx.coroutines.core)
    testFixturesImplementation(project.dependencies.platform(libs.androidx.compose.bom))
    testFixturesImplementation(libs.androidx.compose.runtime) {
        because("https://issuetracker.google.com/issues/259523353#comment32")
    }
}

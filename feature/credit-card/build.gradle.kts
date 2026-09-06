plugins {
    alias(libs.plugins.keygo.android.compose)
}

android {
    namespace = "de.davis.keygo.feature.credit_card"

    testFixtures {
        enable = true
    }
}

dependencies {
    api(projects.core.util)
    implementation(projects.core.security)
    implementation(projects.core.ui)

    implementation(libs.devnied.emvnfccard)

    testFixturesApi(projects.core.util)
    testFixturesImplementation(libs.kotlinx.coroutines.core)
    testFixturesImplementation(project.dependencies.platform(libs.androidx.compose.bom))
    testFixturesImplementation(libs.androidx.compose.runtime) {
        because("https://issuetracker.google.com/issues/259523353#comment32")
    }

    testImplementation(testFixtures(projects.feature.creditCard))
}

plugins {
    alias(libs.plugins.keygo.android.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "de.davis.keygo.feature.settings"

    defaultConfig {
        missingDimensionStrategy("store", "playStore")
    }

    testFixtures {
        enable = true
    }
}

dependencies {
    implementation(projects.core.ui)
    implementation(projects.core.identity)
    implementation(projects.core.item)
    implementation(projects.feature.autofill)
    implementation(projects.feature.backup)

    implementation(libs.androidx.navigation.compose)

    testImplementation(testFixtures(projects.core.identity))
    testImplementation(testFixtures(projects.core.security))
    testImplementation(testFixtures(projects.rust))
    testImplementation(testFixtures(projects.feature.autofill))
    testImplementation(testFixtures(projects.feature.backup))

    testFixturesImplementation(project.dependencies.platform(libs.androidx.compose.bom))
    testFixturesImplementation(libs.androidx.compose.runtime) {
        because("https://issuetracker.google.com/issues/259523353#comment32")
    }
}

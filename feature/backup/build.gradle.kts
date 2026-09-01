plugins {
    alias(libs.plugins.keygo.android.compose)
    alias(libs.plugins.keygo.android.protobuf)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "de.davis.keygo.feature.backup"

    defaultConfig {
        missingDimensionStrategy("store", "playStore")
    }

    testFixtures {
        enable = true
    }
}

dependencies {
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.work)

    implementation(libs.koin.androidx.workmanager)

    implementation(projects.core.ui)
    implementation(projects.core.util)
    implementation(projects.core.item)
    implementation(projects.core.security)
    implementation(projects.rust)
    implementation(projects.feature.item.core)
    implementation(projects.feature.vault)

    testImplementation(testFixtures(projects.core.item))
    testImplementation(testFixtures(projects.core.security))
    testImplementation(testFixtures(projects.rust))
    testImplementation(libs.robolectric)

    testFixturesApi(projects.core.util)
    testFixturesApi(projects.core.security)
    testFixturesImplementation(libs.kotlinx.coroutines.core)
    testFixturesImplementation(project.dependencies.platform(libs.androidx.compose.bom))
    testFixturesImplementation(libs.androidx.compose.runtime) {
        because("https://issuetracker.google.com/issues/259523353#comment32")
    }
}

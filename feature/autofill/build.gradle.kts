plugins {
    alias(libs.plugins.keygo.android.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.parcelize)
}

android {
    namespace = "de.davis.keygo.feature.autofill"

    defaultConfig {
        missingDimensionStrategy("store", "playStore")
    }

    testFixtures {
        enable = true
    }
}

dependencies {
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.androidx.autofill)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.collections.immutable)

    implementation(projects.core.item)
    implementation(projects.core.util)
    implementation(projects.core.ui)
    implementation(projects.core.identity)
    implementation(projects.core.security)
    implementation(projects.feature.item.core)
    implementation(projects.feature.item.create)
    implementation(projects.feature.totp)
    implementation(projects.feature.auth)
    implementation(projects.feature.listScreen)

    testImplementation(testFixtures(projects.core.item))
    testImplementation(testFixtures(projects.core.util))
    testImplementation(testFixtures(projects.core.security))
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.junit)

    testFixturesImplementation(libs.kotlinx.coroutines.core)
    testFixturesImplementation(projects.core.item)
    testFixturesApi(projects.core.util)
    testFixturesApi(projects.feature.totp)
    testFixturesImplementation(project.dependencies.platform(libs.androidx.compose.bom))
    testFixturesImplementation(libs.androidx.compose.runtime) {
        because("https://issuetracker.google.com/issues/259523353#comment32")
    }
}

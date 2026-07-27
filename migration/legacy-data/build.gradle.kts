plugins {
    alias(libs.plugins.keygo.android.library)
    alias(libs.plugins.androidx.room)
    alias(libs.plugins.google.ksp)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "de.davis.keygo.migration.legacy_data"
}

dependencies {
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.kotlinx.serialization.json)

    implementation(projects.core.item)
    implementation(projects.core.security)
    implementation(projects.core.util)

    testImplementation(libs.io.mockk)
    testImplementation(libs.androidx.sqlite.bundled)
    testImplementation(testFixtures(projects.core.item))
    testImplementation(testFixtures(projects.core.security))
    testImplementation(testFixtures(projects.core.util))
}

room {
    schemaDirectory("$projectDir/schemas")
}

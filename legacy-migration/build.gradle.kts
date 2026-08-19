plugins {
    alias(libs.plugins.keygo.android.library)
    alias(libs.plugins.keygo.android.protobuf)
    alias(libs.plugins.androidx.room3)
    alias(libs.plugins.google.ksp)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "de.davis.keygo.legacy_migration"

    testFixtures {
        enable = true
    }
}

dependencies {
    implementation(libs.androidx.datastore)
    implementation(libs.at.favre.bcrypt)

    implementation(libs.androidx.room3.runtime)
    ksp(libs.androidx.room3.compiler)

    implementation(libs.kotlinx.serialization.json)

    implementation(projects.core.item)
    implementation(projects.core.security)
    implementation(projects.core.util)

    testImplementation(libs.io.mockk)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.room3.testing)
    testImplementation(libs.androidx.sqlite.bundled)
    testImplementation(testFixtures(projects.core.item))
    testImplementation(testFixtures(projects.core.security))
    testImplementation(testFixtures(projects.core.util))

    testFixturesImplementation(projects.core.util)
    testFixturesImplementation(libs.androidx.room3.runtime)
}

room3 {
    schemaDirectory("$projectDir/schemas")
}

// The tests seed from v1's exported schema JSON and so need to know where it lives. Handed over as
// a system property rather than resolved from a relative path inside the test, which would only
// work while the working directory happens to be the module directory.
tasks.withType<Test>().configureEach {
    systemProperty("legacySchemaDir", "$projectDir/schemas")
}

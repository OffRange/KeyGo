plugins {
    alias(libs.plugins.keygo.kotlin.jvm)
}

dependencies {
    implementation(libs.google.devtools.ksp.api)

    implementation(project.dependencies.platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.squareup.kotlinpoet)

    implementation(projects.automation)
}

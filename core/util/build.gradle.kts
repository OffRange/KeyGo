import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("java-library")
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.google.ksp)
}
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    implementation(libs.okhttp)

    // Koin DI
    implementation(project.dependencies.platform(libs.koin.bom))
    implementation(project.dependencies.platform(libs.koin.annotations.bom))
    implementation(libs.koin.annotations)
    implementation(libs.koin.core)
    ksp(libs.koin.ksp.compiler)

    testImplementation(libs.kotlin.test)
}
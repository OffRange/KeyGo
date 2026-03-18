// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.androidx.room) apply false
    alias(libs.plugins.kotlin.parcelize) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.git.semantic.versioning) apply false
    alias(libs.plugins.google.ksp) apply false
    alias(libs.plugins.google.protobuf) apply false
    alias(libs.plugins.kotlin.jvm) apply false
}
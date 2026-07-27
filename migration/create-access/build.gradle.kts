plugins {
    alias(libs.plugins.keygo.android.library)
    alias(libs.plugins.keygo.android.protobuf)
}

android {
    namespace = "de.davis.keygo.migration.create_access"
}

dependencies {
    implementation(libs.androidx.datastore)
    implementation(libs.at.favre.bcrypt)
}

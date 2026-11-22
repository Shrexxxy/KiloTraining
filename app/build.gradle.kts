plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose)
}

android {
    namespace = "app.calorietraining"
    compileSdk = 36
    defaultConfig {
        applicationId = "app.calorietraining"
        minSdk = 26
        targetSdk = 36
        versionCode = 40
        versionName = "0.40"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
    implementation(libs.scanner)
    implementation(platform(libs.compose.bom))
    implementation(libs.material3)
    implementation(libs.preview)
    implementation(libs.activity)
    implementation(libs.lifecycle.compose)
    implementation(libs.viewmodel.compose)
    debugImplementation(libs.tooling)
    debugImplementation(libs.ui.manifest)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.ui.test)
}

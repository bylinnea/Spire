plugins {
    alias(libs.plugins.android.application)
    id("com.google.devtools.ksp")
    id("kotlin-parcelize")
}

android {
    namespace = "no.bylinnea.spire"
    compileSdk = 36

    defaultConfig {
        applicationId = "no.bylinnea.spire"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.cardview)

    // Room
    val room_version = "2.6.1"
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // Glide
    implementation(libs.glide)
    ksp(libs.ksp)

    // OkHttp
    implementation(libs.okhttp.v4120)

    // Encrypted prefs
    implementation(libs.androidx.security.crypto.v110alpha06)

    // uCrop
    implementation(libs.ucrop)

    // QR code generation
    implementation(libs.core)

    // QR code scanning
    implementation(libs.zxing.android.embedded)

}
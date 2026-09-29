plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.gms.google.services)
    alias(libs.plugins.google.android.libraries.mapsplatform.secrets.gradle.plugin)
}

android {
    namespace = "com.example.offlinemap"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.offlinemap"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {

    implementation("com.mapbox.maps:android:10.15.0")
    implementation("com.google.firebase:firebase-storage:21.0.1")

    implementation("com.mapbox.mapboxsdk:mapbox-sdk-turf:6.13.0") {
        exclude(group = "com.mapbox.mapboxsdk", module = "mapbox-android-core")
    }

    implementation("com.mapbox.search:mapbox-search-android-ui:1.0.0-beta.38") {
        exclude(group = "com.mapbox.common", module = "okhttp")
        exclude(group = "com.mapbox.mapboxsdk", module = "mapbox-android-core")
    }

    implementation("com.mapbox.search:mapbox-search-android:1.0.0-beta.38") {
        exclude(group = "com.mapbox.common", module = "okhttp")
        exclude(group = "com.mapbox.mapboxsdk", module = "mapbox-android-core")
    }
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.firebase.database)
    implementation(libs.firebase.firestore)
    implementation(libs.play.services.maps)
    implementation(libs.firebase.auth)
    implementation(libs.credentials)
    implementation(libs.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(libs.car.ui.lib)
    implementation(libs.firebase.storage)
    implementation(libs.play.services.location)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}

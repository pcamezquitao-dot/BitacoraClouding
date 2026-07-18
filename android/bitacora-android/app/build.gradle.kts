plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.cactus.bitacora"
    compileSdk = 34
    flavorDimensions += "edition"

    defaultConfig {
        applicationId = "com.cactus.bitacora"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        buildConfigField(
            "String",
            "FACE_TEMPLATE_API_TOKEN",
            "\"${providers.gradleProperty("FACE_TEMPLATE_API_TOKEN").orElse("").get()}\""
        )
    }

    productFlavors {
        create("production") {
            dimension = "edition"
            applicationId = "com.cactus.bitacora"
            manifestPlaceholders["appLabel"] = "Bitacora"
        }
        create("facial1") {
            dimension = "edition"
            applicationId = "com.cactus.bitacora.facial1"
            manifestPlaceholders["appLabel"] = "Bitacora Facial 1"
        }
    }

    // ✅ Alinear Java/Kotlin (soluciona "Inconsistent JVM-target compatibility")
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    testImplementation("junit:junit:4.13.2")

    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    // Modelo empaquetado: la detección facial debe estar disponible sin Internet.
    implementation("com.google.mlkit:face-detection:16.1.7")
    // FaceNet real empaquetado para la prueba técnica aislada (Apache-2.0).
    implementation("org.tensorflow:tensorflow-lite:2.14.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")

    implementation("com.google.zxing:core:3.5.3")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
}

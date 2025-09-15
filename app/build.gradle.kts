plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "ai.runow"
    compileSdk = 34

    defaultConfig {
        applicationId = "ai.runow"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
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

    // ✅ Abilita Compose
    buildFeatures { compose = true }

    // ✅ Compose Compiler compatibile con Kotlin 1.9.24 e Compose 1.7.x
    composeOptions { kotlinCompilerExtensionVersion = "1.5.15" }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    // (facoltativo ma utile con molte lib)
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    // ✅ Compose BOM che porta a Compose 1.7.x (include alignBy/alignByBaseline)
    implementation(platform("androidx.compose:compose-bom:2024.09.02"))

    // Compose UI + Material3 + tooling
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Foundation (Row/Column, layout, ecc.)
    implementation("androidx.compose.foundation:foundation")
    // (Facoltativo) modulo layout dedicato; alcune codebase lo tengono esplicito
    implementation("androidx.compose.foundation:foundation-layout")

    // Icone Material (Icons.Default.*)
    implementation("androidx.compose.material:material-icons-extended")

    // Activity-Compose
    implementation("androidx.activity:activity-compose:1.9.2")

    // AndroidX base
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Test
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.09.02"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

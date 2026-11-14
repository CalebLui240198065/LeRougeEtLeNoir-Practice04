plugins {
    alias(libs.plugins.android.application)
}

android {
    // Keep the original Kotlin and resource namespace when copying the project.
    namespace = "com.rougenoir.app"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        // A new install ID lets the two practice projects coexist on the same device.
        applicationId = "com.rougenoir.app.practice05"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
}

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
        // Keep the install ID from the first published build so its local data upgrades in place.
        applicationId = "com.rougenoir.app.practice05"
        minSdk = 24
        targetSdk = 37
        versionCode = 2
        versionName = "1.1"

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

plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.kooritea.fcmfix"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.kooritea.fcmfix"
        minSdk = 29
        targetSdk = 37
        versionCode = 53
        versionName = "dev"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
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

    lint {
        checkReleaseBuilds = false
    }
}

dependencies {
    compileOnly(libs.libxposed.api)
    implementation(libs.libxposed.interface)
    implementation(libs.libxposed.service)
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    implementation(libs.androidx.activity)
    implementation(libs.androidx.recyclerview)
    implementation(libs.material)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
}

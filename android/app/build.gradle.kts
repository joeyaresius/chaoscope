plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace   = "com.chaoscope"
    compileSdk  = 35

    defaultConfig {
        applicationId = "com.chaoscope"
        minSdk        = 26
        targetSdk     = 35
        versionCode   = 4
        versionName   = "0.1.3"

        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                // arm64-v8a covers all modern devices; x86_64 kept for emulator testing.
                // Bundle splits below mean each user only downloads their device's ABI.
                abiFilters += setOf("arm64-v8a", "x86_64")
            }
        }
    }

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("CHAOSCOPE_KEYSTORE")
                ?: "${rootDir}/keystore.jks"
            val ksFile = file(keystorePath)
            if (ksFile.exists()) {
                storeFile     = ksFile
                storePassword = System.getenv("CHAOSCOPE_KEYSTORE_PASSWORD") ?: ""
                keyAlias      = System.getenv("CHAOSCOPE_KEY_ALIAS")        ?: "chaoscope"
                keyPassword   = System.getenv("CHAOSCOPE_KEY_PASSWORD")     ?: ""
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled   = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Use release signing if the keystore is present, else fall back to debug
            // so `assembleRelease` still works for local smoke-tests.
            val releaseSigning = signingConfigs.getByName("release")
            signingConfig = if (releaseSigning.storeFile != null) {
                releaseSigning
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    bundle {
        abi      { enableSplit = true }
        density  { enableSplit = true }
        language { enableSplit = true }
    }

    externalNativeBuild {
        cmake {
            path    = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }

}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.3")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
}

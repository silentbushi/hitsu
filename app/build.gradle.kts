import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.room)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "app.hitsu.vault"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.hitsu.vault"
        minSdk = 29
        targetSdk = 35
        versionCode = 3
        versionName = "0.2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // yt-dlp ships a Python runtime per ABI; only this phone's architecture is worth carrying.
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    /*
     * The release key lives outside the repository, in keystore.properties. A published APK has to
     * be signed with a key only its author has: the debug key is the same on every machine, so
     * anything signed with it can be installed over the app as if it were an update.
     */
    signingConfigs {
        val properties = Properties().apply {
            val file = rootProject.file("keystore.properties")
            if (file.exists()) file.inputStream().use(::load)
        }
        if (properties.getProperty("storeFile") != null) {
            create("release") {
                storeFile = rootProject.file(properties.getProperty("storeFile"))
                storePassword = properties.getProperty("storePassword")
                keyAlias = properties.getProperty("keyAlias")
                keyPassword = properties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Without the key the release build still compiles; it just comes out unsigned.
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            /*
             * yt-dlp runs its Python binaries from the native library directory, so they have to
             * exist as real files: compressed inside the APK there is nothing to execute.
             */
            useLegacyPackaging = true
        }
    }

    lint {
        /*
         * Aligned16KB: only arm64-v8a is packaged (see abiFilters) and its libpython segments are
         * 64 KB aligned, so 16 KB page devices load it fine; the warning is about ABIs we drop.
         * ChromeOsAbiSupport: this build targets one phone, not ChromeOS.
         */
        disable += setOf("Aligned16KB", "ChromeOsAbiSupport")
    }

    room {
        schemaDirectory("$projectDir/schemas")
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.biometric)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.exifinterface)
    implementation(libs.coil.compose)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.youtubedl.android)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}

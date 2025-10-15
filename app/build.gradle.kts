import java.util.Base64
import java.io.File

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.automathtapper"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.automathtapper"
        minSdk = 33
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        vectorDrawables.useSupportLibrary = true
    }

    // إعدادات التوقيع (اختيارية)
    signingConfigs {
        create("release") {
            val ksB64 = System.getenv("RELEASE_KEYSTORE_BASE64")
            if (ksB64 != null) {
                val ksBytes = Base64.getDecoder().decode(ksB64)
                val tmp = File("${project.buildDir}/tmp-keystore.jks")
                tmp.parentFile.mkdirs()
                tmp.writeBytes(ksBytes)
                storeFile = tmp
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            if (System.getenv("RELEASE_KEYSTORE_BASE64") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
    }
    packaging.resources.excludes.add("META-INF/*")
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity-ktx:1.9.2")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    // OCR مكتبة التعرف على النصوص
    implementation("com.google.mlkit:text-recognition:16.0.0")
}

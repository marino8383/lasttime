import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "it.marino8383.lasttime"
    compileSdk = 34

    defaultConfig {
        applicationId = "it.marino8383.lasttime"
        minSdk = 26
        targetSdk = 34
        versionCode = 17
        versionName = "0.6.2"

        buildConfigField("String", "BUILD_TIME", "\"${buildTimestamp()}\"")
    }

    // Chiave debug fissa committata nel repo: senza, ogni build CI firmerebbe con una
    // chiave diversa e Android rifiuterebbe l'aggiornamento sopra la versione installata.
    signingConfigs {
        create("shared") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "lasttimedebug"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("shared")
        }
        release {
            signingConfig = signingConfigs.getByName("shared")
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
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// import espliciti in testa: nello script "java" da solo verrebbe risolto
// come estensione del plugin Java, oscurando i package java.*
fun buildTimestamp(): String {
    val fmt = SimpleDateFormat("dd/MM/yyyy HH:mm")
    fmt.timeZone = TimeZone.getTimeZone("Europe/Rome")
    return fmt.format(Date())
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.core:core-ktx:1.13.1")
    // 2.8.4: le 2.8.0 crashano all'avvio con Compose 1.6 (LocalLifecycleOwner not present)
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
}

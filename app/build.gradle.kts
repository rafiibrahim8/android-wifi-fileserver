import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// Signing material lives in `keystore.properties` at the repo root. The file is
// gitignored; an `keystore.properties.example` is committed for reference.
val keystorePropertiesFile: File = rootProject.file("keystore.properties")
val hasKeystore: Boolean = keystorePropertiesFile.exists()
val keystoreProperties = Properties().apply {
    if (hasKeystore) keystorePropertiesFile.inputStream().use(::load)
}

android {
    namespace = "me.ibrahimrafi.wififileserver"
    compileSdk = 36

    defaultConfig {
        applicationId = "me.ibrahimrafi.wififileserver"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasKeystore) {
            create("release") {
                val storeFileName = keystoreProperties.getProperty("storeFile")
                    ?: error("keystore.properties missing 'storeFile'")
                storeFile = rootProject.file(storeFileName)
                storePassword = keystoreProperties.getProperty("storePassword")
                    ?: error("keystore.properties missing 'storePassword'")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                    ?: error("keystore.properties missing 'keyAlias'")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                    ?: error("keystore.properties missing 'keyPassword'")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.nanohttpd)
    implementation(libs.androidx.documentfile)
    implementation(libs.zxing.core)
    implementation(libs.material)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.service)

    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.timber)

    testImplementation(libs.junit)
}

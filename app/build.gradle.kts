plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.bkuch.ytmspotify"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.bkuch.ytmspotify"
        minSdk = 26
        targetSdk = 35
        // CI run number, so every build installs as an update of the previous one.
        val build = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionCode = build
        versionName = "1.0.$build"
    }

    // Stable signing key from CI secrets (see README), so APKs from different runs can be
    // installed over each other. Without it, builds fall back to the per-machine debug key.
    val keystore = System.getenv("SIGNING_STORE_FILE")?.let { file(it) }?.takeIf { it.exists() }
    if (keystore != null) {
        signingConfigs {
            create("stable") {
                storeFile = keystore
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            if (keystore != null) signingConfig = signingConfigs.getByName("stable")
        }
        release {
            isMinifyEnabled = false
            if (keystore != null) signingConfig = signingConfigs.getByName("stable")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "vpn.moonlight"
    compileSdk = 36

    defaultConfig {
        applicationId = "vpn.moonlight"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.4"

        // The panel these subscriptions live on. Override per flavour/build.
        buildConfigField("String", "DEFAULT_PANEL_HOST", "\"sub.moonlight.vpn\"")
        buildConfigField("String", "TELEGRAM_BOT_URL", "\"https://t.me/moonlight_vpn_bot\"")
        buildConfigField("String", "TELEGRAM_CHANNEL_URL", "\"https://t.me/moonlight_vpn\"")
        buildConfigField("String", "SUPPORT_URL", "\"https://t.me/moonlight_support\"")
    }

    // A ~50 MB libgojni.so per ABI makes a universal APK unreasonable, so
    // release builds are split per ABI and a device downloads one.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
            // Off by default so nobody ships a 123 MB universal APK by accident.
            // Build one with -PuniversalApk when you don't know the target ABI:
            //   ./gradlew assembleDebug -PuniversalApk
            isUniversalApk = project.hasProperty("universalApk")
        }
    }

    bundle {
        // Language splits must stay off: the app has its own RU/EN switch, and
        // with per-language delivery the strings for the other language would
        // simply not be installed.
        language { enableSplit = false }
    }

    androidResources {
        localeFilters += listOf("en", "ru")
    }

    signingConfigs {
        create("release") {
            // Supplied by CI from repository secrets, or by a local
            // gradle.properties / environment for a local release build.
            //
            // There is deliberately no fallback key. A signing key committed to
            // the repository is a public key: it keeps update-in-place working,
            // but anyone can then build an APK that installs as an update over
            // this one. An unsigned build that fails loudly is the safer default.
            val store = System.getenv("MOONLIGHT_KEYSTORE")
                ?: project.findProperty("moonlight.keystore") as String?

            if (store != null && file(store).exists()) {
                storeFile = file(store)
                storePassword = System.getenv("MOONLIGHT_KEYSTORE_PASSWORD")
                    ?: project.findProperty("moonlight.keystore.password") as String?
                keyAlias = System.getenv("MOONLIGHT_KEY_ALIAS")
                    ?: project.findProperty("moonlight.key.alias") as String?
                keyPassword = System.getenv("MOONLIGHT_KEY_PASSWORD")
                    ?: project.findProperty("moonlight.key.password") as String?
            }
        }
    }

    buildTypes {
        release {
            // Left unsigned when no key is configured, rather than falling back
            // to something checked in. The release workflow refuses to run
            // without the secrets, so an unsigned APK never reaches a release.
            signingConfig = signingConfigs.getByName("release").takeIf {
                it.storeFile != null
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
        jniLibs { useLegacyPackaging = true }
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":design"))
    implementation(project(":data"))
    implementation(project(":vpn"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.androidx.core.ktx)
    // Carries AppCompatDelegate.setApplicationLocales, the per-app locale
    // backport for API 26..32 where LocaleManager does not exist.
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.work)

    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.mlkit.barcode)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "vpn.moonlight.data"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        testOptions.targetSdk = 36
    }

    // MoonlightLog mirrors everything to android.util.Log, which throws in JVM
    // tests. The logger is genuinely Android-dependent; stubbing the framework is
    // the point of this flag rather than a workaround.
    testOptions { unitTests.isReturnDefaultValues = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    api(libs.kotlinx.coroutines.android)
    api(libs.kotlinx.serialization.json)
    api(libs.androidx.datastore.preferences)
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

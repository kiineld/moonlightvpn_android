plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "vpn.moonlight.core"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        testOptions.targetSdk = 36
        buildConfigField("String", "XRAY_VERSION", "\"${libs.versions.xrayCore.get()}\"")
        buildConfigField("String", "TUN2SOCKS_VERSION", "\"${libs.versions.hevSocks5Tunnel.get()}\"")
    }

    // MoonlightLog mirrors everything to android.util.Log, which throws in JVM
    // tests. The logger is genuinely Android-dependent; stubbing the framework is
    // the point of this flag rather than a workaround.
    testOptions { unitTests.isReturnDefaultValues = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { buildConfig = true }

    // libtun2socks.so is an executable, not a JNI library: it must be extracted
    // to nativeLibraryDir so the service can exec it.
    packaging { jniLibs { useLegacyPackaging = true } }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    api(project(":data"))
    // Xray-core, bound via gomobile. Fetched by scripts/fetch-native.sh.
    api(group = "", name = "libXray", ext = "aar")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.service)
    // The latency probe dials through the local SOCKS proxy itself, because
    // libXray's own ping disables keep-alives and so measures handshakes.
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

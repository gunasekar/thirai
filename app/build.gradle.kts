import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    id("com.squareup.wire") version "6.2.0"
}

// Release signing is read from a gitignored keystore.properties at the repo
// root (see README). Absent it, debug builds still work; release stays
// unsigned until the file is present.
val keystoreProperties = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) FileInputStream(f).use { load(it) }
}
val hasReleaseKeystore = keystoreProperties.isNotEmpty()

// The git tag is the single source of truth for the version — there are no
// hardcoded numbers to drift out of sync. `versionName` is the tag with its
// leading `v` stripped (v0.2.0 → 0.2.0); a build off an untagged commit gets a
// descriptive suffix (0.2.0-3-gabc123). `versionCode` is the commit count, so
// it increases monotonically. Release ritual: `git tag vX.Y.Z && git push --tags`.
fun git(vararg args: String): String? =
    runCatching {
        providers.exec { commandLine("git", *args) }.standardOutput.asText.get().trim()
    }.getOrNull()?.takeIf { it.isNotEmpty() }

val gitVersionName: String = (git("describe", "--tags", "--dirty", "--always") ?: "0.0.0").removePrefix("v")
val gitVersionCode: Int = git("rev-list", "--count", "HEAD")?.toIntOrNull() ?: 1

// The default show-source URL is not hardcoded in the app — it comes from the
// gitignored .env (THIRAI_SHOWS_URL), injected into BuildConfig at build time. A
// clone without .env builds with an empty default; the user sets the source
// in-app (Show source / Scan QR).
fun envValue(key: String): String {
    val f = rootProject.file(".env")
    if (f.exists()) {
        f.readLines().forEach { line ->
            val t = line.trim()
            if (t.startsWith("#") || "=" !in t) return@forEach
            val (k, rawV) = t.removePrefix("export ").split("=", limit = 2)
            if (k.trim() == key) return rawV.trim().trim('"')
        }
    }
    return System.getenv(key) ?: ""
}
val showsUrl: String = envValue("THIRAI_SHOWS_URL")

android {
    namespace = "com.thirai"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.thirai"
        minSdk = 26
        targetSdk = 35
        versionCode = gitVersionCode
        versionName = gitVersionName

        buildConfigField("String", "SHOWS_URL", "\"$showsUrl\"")
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
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
        buildConfig = true
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            // BouncyCastle ships payloads we never touch: post-quantum (picnic)
            // key data and localized cert-path message bundles. Drop them.
            excludes += "org/bouncycastle/pqc/**"
            excludes += "org/bouncycastle/**/*Messages*.properties"
        }
    }

    sourceSets {
        named("main") {
            kotlin.srcDirs("src/main/kotlin")
        }
    }
}

// Generate Kotlin classes for the Android TV Remote v2 protocol (pairing +
// control) from the .proto files in src/main/proto.
wire {
    kotlin {}
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    // Jetpack Compose — the phone app's premium setup/debug UI.
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    // Android TV Remote v2 transport: Wire for the protobuf messages, and
    // BouncyCastle to mint the self-signed client certificate used for pairing.
    implementation("com.squareup.wire:wire-runtime:6.2.0")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.78.1")

    // Glide loads poster bitmaps for the home-screen widget's RemoteViews.
    implementation("com.github.bumptech.glide:glide:4.16.0")
    // Coil loads poster thumbnails in the Compose setup screen.
    implementation("io.coil-kt:coil-compose:2.7.0")
    // ZXing generates and scans the setup QR (share the show-source URL).
    implementation(libs.zxing.android.embedded)
    testImplementation(libs.junit)
}

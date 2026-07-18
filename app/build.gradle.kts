import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
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

android {
    namespace = "com.thirai"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.thirai"
        minSdk = 26
        targetSdk = 35
        versionCode = gitVersionCode
        versionName = gitVersionName
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
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    sourceSets {
        named("main") {
            kotlin.srcDirs("src/main/kotlin")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    implementation("com.tananaev:adblib:1.3")
    implementation("com.github.bumptech.glide:glide:4.16.0")
    testImplementation(libs.junit)
}

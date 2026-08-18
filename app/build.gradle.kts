import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}
val malClientId = localProperties.getProperty("MAL_CLIENT_ID", "")

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Version from git tag
val gitTagOutput = providers.exec {
    commandLine("git", "describe", "--tags", "--abbrev=0")
    isIgnoreExitValue = true
}.standardOutput.asText.get().trim()
val appVersionName = gitTagOutput.removePrefix("v").ifBlank { "0.0.0" }
val appVersionCode = appVersionName.split(".")
    .map { it.toIntOrNull() ?: 0 }
    .let { (maj, min, patch) -> maj * 10000 + min * 100 + patch }

android { namespace = "com.kiko.tracker"; compileSdk = 35
    defaultConfig {
        applicationId = "com.kiko.tracker"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
        buildConfigField("String", "MAL_CLIENT_ID", "\"$malClientId\"")
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // There was no release buildType at all before this, which meant a "release" build
    // silently fell back to every default: unminified, unshrunk, and debuggable-adjacent.
    // That matters a lot more than it looks for "does the app feel smooth" — a debug
    // Compose build skips R8's method inlining/devirtualization and keeps full debug
    // metadata on every composable, so the exact same code measurably recomposes and
    // redraws slower than the same APK built for release. If testing has mostly been
    // "Run" from Android Studio, a chunk of the sluggishness may be that alone — worth
    // installing a release APK (via GitHub Actions or `./gradlew assembleRelease`) and
    // comparing side by side before chasing further code-level fixes.
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")
    implementation("io.coil-kt:coil-compose:2.7.0")
    // Adds animated GIF/WebP decoding to Coil — without this, AsyncImage silently only ever
    // decodes and shows a GIF's first frame instead of playing it (see MainActivity.onCreate,
    // which registers the actual decoders with Coil's singleton ImageLoader).
    implementation("io.coil-kt:coil-gif:2.7.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.browser:browser:1.8.0")
    implementation("androidx.webkit:webkit:1.12.1")
    implementation("org.jsoup:jsoup:1.17.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
    // Installs src/main/baseline-prof.txt (below) onto the device on first run of a
    // release build, on API levels where the OS doesn't already read baseline profiles
    // straight from the APK. Without this dependency, a hand-authored or generated
    // baseline profile just sits unused in the APK — this is what actually applies it.
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
}
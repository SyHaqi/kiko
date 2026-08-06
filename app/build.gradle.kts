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

android { namespace = "com.kiko.tracker"; compileSdk = 35
    defaultConfig { applicationId = "com.kiko.tracker"; minSdk = 26; targetSdk = 35; versionCode = 3; versionName = "2.2.0"; buildConfigField("String", "MAL_CLIENT_ID", "\"$malClientId\"") }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
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
    debugImplementation("androidx.compose.ui:ui-tooling")
}
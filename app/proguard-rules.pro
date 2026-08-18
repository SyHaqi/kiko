# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /usr/local/android-sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.kts.
#
# For most projects, this file's default content is enough — Coil, OkHttp, Jsoup, and
# AndroidX Compose all ship their own consumer ProGuard rules inside their AARs, so R8
# already knows what to keep in each of them without anything added here. This app also
# doesn't use reflection-based JSON (Gson/kotlinx.serialization) — MalApi and friends parse
# with org.json.JSONObject by string key, which has nothing for R8 to accidentally strip.
#
# If a release build ever crashes with a NoSuchMethodError/ClassNotFoundException that a
# debug build doesn't, that's the signature of R8 having stripped something it shouldn't
# have — add a targeted -keep rule for that specific class here rather than disabling
# minification, so the rest of the app keeps the size/speed benefit.

# Keep line numbers for readable stack traces in crash reports (CrashReporting.kt writes
# raw stack traces to a file — without this they'd be de-obfuscated garbage).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

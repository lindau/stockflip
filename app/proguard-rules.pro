# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep application entry point
-keep class com.stockflip.StockFlipApplication

# Keep Retrofit API interface and Gson-backed response fields.
-keep interface com.stockflip.YahooFinanceApi { *; }
-keep class com.stockflip.YahooFinanceResponse { <fields>; }
-keep class com.stockflip.Chart { <fields>; }
-keep class com.stockflip.YahooError { <fields>; }
-keep class com.stockflip.Result { <fields>; }
-keep class com.stockflip.Meta { <fields>; }
-keep class com.stockflip.Indicators { <fields>; }
-keep class com.stockflip.Quote { <fields>; }

# Keep worker names stable across app upgrades and preserve required constructors.
-keepnames class * extends androidx.work.ListenableWorker
-keepclassmembers class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Retrofit rules
-keepattributes Signature
-keepattributes *Annotation*
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# Gson rules
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# OkHttp rules
-keepattributes Signature
-keepattributes *Annotation*
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# Strip Log calls from release builds.
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
    public static *** println(...);
}

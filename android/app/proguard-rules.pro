# Real R8/minification keep rules (2026-08-09) -- added alongside enabling isMinifyEnabled +
# isShrinkResources on the release build type, found disabled during a Toss-parity performance
# audit (no shrinking/obfuscation on release = bigger APK, slower cold start, trivially
# reverse-engineerable binary). Retrofit and OkHttp ship their own consumer-rules.pro inside
# their AARs and don't need manual rules here; Gson does NOT protect application model classes
# automatically -- it reflects over field names at runtime, so any DTO R8 renames or strips
# silently breaks (de)serialization with no compile-time warning, only a runtime crash or (worse)
# silently-null fields. This is the one keep rule this app's own reflection use actually requires.

# All Retrofit response/request DTOs live in these packages across the client modules that talk
# to services/backend directly -- keep every field name and the no-arg constructor Gson needs.
-keepclassmembers class rw.itunda.core.network.** {
    <fields>;
}
-keep class rw.itunda.core.network.** { *; }

# Gson itself uses reflection on TypeToken generics.
-keep class com.google.gson.reflect.TypeToken
-keep class * extends com.google.gson.reflect.TypeToken
-keepattributes Signature
-keepattributes *Annotation*

# Kotlin data class synthetic methods (componentN/copy) referenced reflectively by some Gson
# adapters on generic collection fields.
-keepclassmembers class rw.itunda.core.network.** {
    ** component*();
    ** copy(...);
}

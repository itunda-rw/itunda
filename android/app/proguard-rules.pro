# Real R8/minification keep rules (2026-08-09) -- added alongside enabling isMinifyEnabled +
# isShrinkResources on the release build type, found disabled during a Toss-parity performance
# audit (no shrinking/obfuscation on release = bigger APK, slower cold start, trivially
# reverse-engineerable binary). Gson does NOT protect application model classes automatically --
# it reflects over field names at runtime, so any DTO R8 renames or strips silently breaks
# (de)serialization with no compile-time warning, only a runtime crash or (worse) silently-null
# fields.

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

# Real fix (2026-08-09), found live on the physical device via a genuine crash (not silent
# failure): the initial "-keepattributes Signature" above was NOT sufficient on its own. Every
# suspend `ApiService`/`AuthApi` call (e.g. getWallets()) crashed with
# "java.lang.ClassCastException: java.lang.Class cannot be cast to java.lang.reflect.
# ParameterizedType" inside Retrofit's dynamic proxy -- Retrofit's Kotlin-coroutine adapter
# resolves a suspend function's real return type by reflecting on its synthetic
# kotlin.coroutines.Continuation<? super T> parameter's generic signature; R8 full mode (AGP's
# default) strips that generic info for any class not itself kept, and Continuation isn't kept
# by default. This is Retrofit's own documented, official required rule
# (square/retrofit's proguard-rules.pro) -- was missing entirely before this fix, and my initial
# assumption that "Retrofit/OkHttp ship their own consumer-rules.pro, don't need manual rules
# here" was wrong for this specific Kotlin-suspend-function case.
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keepattributes InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn org.codehaus.mojo.animal_sniffer.AnnotationStub
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

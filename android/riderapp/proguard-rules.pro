# Real R8/minification keep rules (2026-08-09), same Toss-parity fix as :app/proguard-rules.pro.
# Gson does not protect application model classes automatically -- it reflects over field names
# at runtime, so any DTO R8 renames or strips silently breaks (de)serialization with no
# compile-time warning.
-keepclassmembers class rw.itunda.rider.network.** {
    <fields>;
}
-keep class rw.itunda.rider.network.** { *; }
-keep class com.google.gson.reflect.TypeToken
-keep class * extends com.google.gson.reflect.TypeToken
-keepattributes Signature
-keepattributes *Annotation*
-keepclassmembers class rw.itunda.rider.network.** {
    ** component*();
    ** copy(...);
}

# Real fix (2026-08-09), same crash found live on :app -- Retrofit's Kotlin-coroutine adapter
# needs kotlin.coroutines.Continuation's generic signature kept, or every suspend API call
# crashes with "Class cannot be cast to ParameterizedType". Retrofit's own official required rule.
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

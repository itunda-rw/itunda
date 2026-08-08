# Real R8/minification keep rules (2026-08-09), same Toss-parity fix as :app/proguard-rules.pro.
# Gson does not protect application model classes automatically -- it reflects over field names
# at runtime, so any DTO R8 renames or strips silently breaks (de)serialization with no
# compile-time warning.
-keepclassmembers class rw.itunda.agent.network.** {
    <fields>;
}
-keep class rw.itunda.agent.network.** { *; }
-keep class com.google.gson.reflect.TypeToken
-keep class * extends com.google.gson.reflect.TypeToken
-keepattributes Signature
-keepattributes *Annotation*
-keepclassmembers class rw.itunda.agent.network.** {
    ** component*();
    ** copy(...);
}

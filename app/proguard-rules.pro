# TikTokDJMixer ProGuard Rules
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception

-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}

-dontwarn io.ktor.**
-keep class io.ktor.** { *; }

-keep class com.tiktokdj.mixer.model.** { *; }

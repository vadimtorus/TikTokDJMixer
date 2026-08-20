# TikTokDJMixer ProGuard Rules
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception

# Kotlinx Serialization
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keepclassmembers class **$$serializer { *; }
-keep class kotlinx.serialization.json.** { *; }
-keep class kotlinx.serialization.internal.** { *; }
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}

# Ktor
-dontwarn io.ktor.**
-keep class io.ktor.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *
-keepclassmembers class * {
    @androidx.room.* <fields>;
    @androidx.room.* <methods>;
}

# Glide
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule { <init>(...); }
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
  **[] $VALUES;
  public *;
}
-keep class com.bumptech.glide.load.data.ParcelFileDescriptorRewinder$InternalRewinder { *** rewind(); }

# Media3 / ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# App models
-keep class com.tiktokdj.mixer.model.** { *; }

# AAC encoder
-keep class net.sourceforge.aacenc.** { *; }

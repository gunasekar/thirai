# Thirai ProGuard Rules

# Keep serialization classes
-keepclassmembers class com.thirai.Show { *; }
-keepclassmembers class com.thirai.ShowConfig { *; }
-keep class com.thirai.Show$$serializer { *; }
-keep class com.thirai.ShowConfig$$serializer { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Glide
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule { <init>(...); }
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
  **[] $VALUES;
  public *;
}

# ADB lib
-keep class com.tananaev.adblib.** { *; }

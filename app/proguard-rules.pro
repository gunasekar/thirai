# Thirai — R8/ProGuard rules for the release build.

# --- kotlinx.serialization (Show / ShowConfig / AppConfig) ---
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.thirai.config.**$$serializer { *; }
-keepclassmembers class com.thirai.config.** { *; }

# --- Wire (Android TV Remote protobuf messages) ---
# Generated messages carry static ADAPTER fields used to encode/decode on the
# wire; keep the message classes and Wire runtime intact.
-keep class remote.** { *; }
-keep class com.google.polo.wire.protobuf.** { *; }
-dontwarn com.squareup.wire.**

# --- ZXing / journeyapps (setup QR scan + generate) ---
-keep class com.journeyapps.barcodescanner.** { *; }
-dontwarn com.google.zxing.**

# --- OkHttp / okio (config fetch) ---
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# --- Glide (widget poster bitmaps) ---
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule { <init>(...); }
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
  **[] $VALUES;
  public *;
}

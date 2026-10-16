# Referenced only from AndroidManifest meta-data, which R8 cannot see.
-keep class com.scoreup.app.cast.CastOptionsProvider { *; }
# Sync / persistence models are serialized with kotlinx.serialization (generated serializers).
-keep,includedescriptorclasses class com.scoreup.core.**$$serializer { *; }
-keepclassmembers class com.scoreup.core.** { *** Companion; kotlinx.serialization.KSerializer serializer(...); }
-dontwarn org.conscrypt.**
-dontwarn com.android.org.conscrypt.**
-dontwarn org.apache.harmony.xnet.provider.jsse.**

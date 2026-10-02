# Backup DTOs are serialized with kotlinx.serialization (rules shipped by the library cover generated serializers).
-keepattributes *Annotation*, InnerClasses

# ML Kit (barcode scanner): components are registered and looked up by reflection
# (ComponentRegistrar declared in the manifest). R8 full mode broke that lookup in release builds:
# BarcodeScanning.getClient() crashed with a NullPointerException. Keep ML Kit classes intact.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_** { *; }
-keep class com.google.firebase.components.** { *; }
-dontwarn com.google.mlkit.**

# Preserve line number information for crash stack traces without revealing source paths
-keepattributes SourceFile,LineNumberTable,*Annotation*
-renamesourcefileattribute SourceFile

# Strip verbose/debug/info logs from release builds so peer IDs, keys, and IPs never leak
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# LibSignal Client JNI rules
-keep class org.whispersystems.libsignal.** { *; }
-keep class org.signal.** { *; }

# Kotlinx Serialization
-keep @kotlinx.serialization.Serializable class * { *; }

# ML Kit / Firebase ComponentDiscovery instantiates ComponentRegistrars via no-arg reflection.
# R8 full mode removes <init>() unless constructors are explicitly kept.
-keep class * implements com.google.firebase.components.ComponentRegistrar {
    <init>();
}
-keepclassmembers class * implements com.google.firebase.components.ComponentRegistrar {
    <init>();
}

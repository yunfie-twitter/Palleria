# ---------------------------------------------------------------------------
# Debuggability: retain enough information to retrace release crashes.
# ---------------------------------------------------------------------------
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ---------------------------------------------------------------------------
# Kotlin metadata needed by reflection-based libraries, if applicable.
# Remove this block if you do not use Kotlin reflection / serialization.
# ---------------------------------------------------------------------------
-keepattributes *Annotation*,InnerClasses,EnclosingMethod,Signature

# ---------------------------------------------------------------------------
# UniFFI / JNA
# JNA resolves generated bindings through JNI names, so this boundary must
# not be renamed or removed.
# ---------------------------------------------------------------------------
-keep class com.sun.jna.** { *; }
-keep class com.yunfie.illustia.rust.** { *; }
-keep interface com.yunfie.illustia.rust.** { *; }

-keep class * extends com.sun.jna.Structure { *; }
-keepclassmembers class * extends com.sun.jna.Structure {
    <fields>;
    <init>();
}
-keep interface * extends com.sun.jna.Library { *; }
-keep interface * extends com.sun.jna.Callback { *; }
-keepclassmembers class * implements com.sun.jna.Callback {
    <methods>;
}
-dontwarn com.sun.jna.**

# ---------------------------------------------------------------------------
# Shizuku
# Keep only the reflective process-entry point unless the library's own
# consumer rules are insufficient.
# ---------------------------------------------------------------------------
-keepclassmembers class rikka.shizuku.Shizuku {
    private static *** newProcess(...);
}

# ---------------------------------------------------------------------------
# Warnings that are safe to suppress
# ---------------------------------------------------------------------------
-dontwarn coil3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.j2objc.annotations.**
-dontwarn org.slf4j.**
-dontwarn com.my.kizzyrpc.**
-dontwarn org.java_websocket.**

# ---------------------------------------------------------------------------
# General Attributes & Line Numbers
# ---------------------------------------------------------------------------
-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature, SourceFile, LineNumberTable

# ---------------------------------------------------------------------------
# Kotlin Objects, Companions, Singletons & Enums
# ---------------------------------------------------------------------------
-keepclasseswithmembers class * {
    public static final ** INSTANCE;
}

-keepclasseswithmembers class * {
    public static final ** Companion;
}

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keep class kotlin.jvm.internal.DefaultConstructorMarker { *; }

# ---------------------------------------------------------------------------
# Jetpack Compose
# ---------------------------------------------------------------------------
-keepclassmembers class * extends androidx.compose.runtime.snapshots.SnapshotState { *; }
-keepclassmembers class * implements androidx.compose.runtime.State { *; }
-keep class **.ComposableSingletons$* { *; }
-keepclassmembers class **.ComposableSingletons$* {
    public static final ** INSTANCE;
    public static final ** lambda-*;
    public static final ** lambda$*;
    *;
}
-keep class androidx.compose.runtime.internal.ComposableLambdaImpl { *; }
-keep class androidx.compose.runtime.internal.ComposableLambda { *; }

# ---------------------------------------------------------------------------
# Miuix UI Library
# ---------------------------------------------------------------------------
-keep class top.yukonga.miuix.kmp.** { *; }
-keepclassmembers class top.yukonga.miuix.kmp.** {
    public static final ** INSTANCE;
    *;
}
-dontwarn top.yukonga.miuix.kmp.**

# Coil
-dontwarn coil3.**

# OkHttp
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepclassmembers class okhttp3.internal.publicsuffix.PublicSuffixDatabase {
    private java.lang.String[] publicSuffixList;
    private java.lang.String[] publicSuffixExceptionList;
}
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**

# Tink / Security-Crypto
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.j2objc.annotations.**

# JNA is used by the generated UniFFI Rust bindings. Its native dispatcher
# resolves Java classes and fields by their original JNI names at runtime.
-keep class com.sun.jna.** { *; }
# UniFFI's generated bindings include JNA-reflected library interfaces,
# structures, callbacks, converters, and error types. Keep the generated
# boundary intact so R8 cannot rename or remove members required by JNA.
-keep class com.yunfie.illustia.rust.** { *; }
-keep interface com.yunfie.illustia.rust.** { *; }
-keep class * extends com.sun.jna.Structure { *; }
-keep interface * extends com.sun.jna.Library { *; }
-keep interface * extends com.sun.jna.Callback { *; }
-dontwarn com.sun.jna.**

# KizzyRPC, Java-WebSocket & SLF4J
-dontwarn org.slf4j.**
-dontwarn com.my.kizzyrpc.**
-dontwarn org.java_websocket.**
-keep class com.my.kizzyrpc.** { *; }
-keepclassmembers class com.my.kizzyrpc.** { *; }
-keep class org.java_websocket.** { *; }
-keepclassmembers class org.java_websocket.** { *; }
# Shizuku Process Reflection
-keepclassmembers class rikka.shizuku.Shizuku {
    private static *** newProcess(...);
}
-keep class rikka.shizuku.** { *; }

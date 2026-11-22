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

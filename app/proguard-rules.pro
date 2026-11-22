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
# Tink references Error Prone annotations that are compile-time only.
# They are not required on the Android runtime classpath.
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.CheckReturnValue
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn com.google.errorprone.annotations.RestrictedApi

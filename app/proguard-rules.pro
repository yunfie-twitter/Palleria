# Compose
-keepclassmembers class * extends androidx.compose.runtime.snapshots.SnapshotState { *; }

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

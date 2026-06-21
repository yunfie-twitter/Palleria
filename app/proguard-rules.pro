# Optimization
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*
-optimizationpasses 5
-allowaccessmodification

# Compose
-keepclassmembers class * extends androidx.compose.runtime.snapshots.SnapshotState { *; }

# Coil
-keep class coil3.** { *; }
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

# Serialization
-keep class kotlinx.serialization.json.** { *; }
-keepattributes *Annotation*, EnclosingMethod, Signature
-keepclassmembers class ** {
    @kotlinx.serialization.Serializable *;
}

# Illustia Models (Need to keep for serialization)
-keep class com.yunfie.illustia.data.** { *; }
-keepclassmembers class com.yunfie.illustia.data.** { *; }

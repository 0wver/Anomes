# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep annotations
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes SourceFile,LineNumberTable
-keepattributes InnerClasses,EnclosingMethod

# Keep data classes for Room
-keep class com.example.anomess.data.** { *; }

# Keep network classes
-keep class com.example.anomess.network.** { *; }

# Room Database
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-keepclassmembers class kotlin.coroutines.SafeContinuation {
    volatile <fields>;
}

# Tor Libraries
-keep class net.freehaven.tor.control.** { *; }
-keep class info.guardianproject.** { *; }
-dontwarn org.slf4j.**

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keepclassmembers,allowobfuscation class * {
  @com.google.gson.annotations.SerializedName <fields>;
}

# Coil
-dontwarn coil.**

# Native Libraries (Tor binary)
-keep class **.BuildConfig { *; }
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# Compose
-dontwarn androidx.compose.**

# Keep Entry Points
# Keep Entry Points
-keep class com.example.anomess.MainActivity { *; }
-keep class com.example.anomess.AnomessApp { *; }

# --- CRASH FIXES ---

# SQLCipher
-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }

# Google Tink (Jetpack Security) & Protobuf
-keep class com.google.crypto.tink.** { *; }
-keep class com.google.protobuf.** { *; }
-keep class androidx.security.crypto.** { *; }

# JNA (if used by any internal lib)
-dontwarn com.sun.jna.**
-keep class com.sun.jna.** { *; }

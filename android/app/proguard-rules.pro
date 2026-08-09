# Add project-specific ProGuard rules here.
# For more details, see https://developer.android.com/build/shrink-code

-keepattributes Signature
-keepattributes *Annotation*

# Keep kotlinx.serialization generated serializers
-keepclassmembers class kotlin.Metadata { *; }
-keepclassmembers @kotlinx.serialization.Serializable class * {
    *;
}

# Retrofit / OkHttp
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *

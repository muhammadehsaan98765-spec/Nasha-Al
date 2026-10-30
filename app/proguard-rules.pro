# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.nashaai.assistant.**$$serializer { *; }
-keepclassmembers class com.nashaai.assistant.** {
    *** Companion;
}
-keepclasseswithmembers class com.nashaai.assistant.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**

# Tink
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**

# Keep data classes used for serialization
-keep class com.nashaai.assistant.data.** { *; }
-keep class com.nashaai.assistant.domain.** { *; }

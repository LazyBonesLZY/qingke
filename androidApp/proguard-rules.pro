-keepattributes *Annotation*, InnerClasses, Signature, Exception
-keep class cn.edu.gzus.qingke.data.** { *; }
-keepclassmembers class cn.edu.gzus.qingke.data.** { *; }
-dontwarn kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class **$$serializer {
    *;
}
-keep class io.ktor.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.slf4j.**
-dontwarn kotlinx.datetime.**
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean
-keep class cn.edu.gzus.qingke.widget.** { *; }

-keepclasseswithmembernames class de.davisalessandro.keygo.** {
    *;
}

# Keep all JNA classes and their members
-keep class com.sun.jna.** { *; }

# Keep class members of any classes extending JNA classes (Structures, Unions, etc.)
-keepclassmembers class * extends com.sun.jna.** { *; }

# JNA sometimes references java.awt internally, which Android doesn't have.
# This prevents R8 from throwing warnings about it.
-dontwarn java.awt.**
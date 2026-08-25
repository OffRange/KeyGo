# Protobuf Lite
-keep class com.google.protobuf.** { *; }

# Keep generated protobuf message fields
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}

# Keep protobuf enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# DataStore Proto
-keep class androidx.datastore.** { *; }

-keep class * extends androidx.room3.RoomDatabase { <init>(); }
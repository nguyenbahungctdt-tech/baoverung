# Project-specific ProGuard rules for R8 optimization.

# --- General Optimizations ---
-keepattributes SourceFile,LineNumberTable,Signature,InnerClasses,EnclosingMethod,*Annotation*

# --- Data Models (Keep names for JSON/Database serialization) ---
-keepclassmembers class com.baoverung.app.data.local.entity.** { *; }
-keep class com.baoverung.app.data.local.entity.** { *; }
-keepclassmembers class com.baoverung.app.data.model.** { *; }
-keep class com.baoverung.app.data.model.** { *; }
-keep class com.baoverung.app.gis.CoordinateSystem { *; }

# --- Moshi Rules ---
-keep class com.squareup.moshi.** { *; }
-keep interface com.squareup.moshi.** { *; }
-keepclassmembers class * {
    @com.squareup.moshi.Json *;
}

# --- Room Rules ---
-keep class * extends androidx.room.RoomDatabase
-keep class * extends androidx.room.Entity
-keep class * extends androidx.room.Dao

# --- Firebase Rules ---
-keep class com.google.firebase.** { *; }
-keep interface com.google.firebase.** { *; }

# --- POI (Word Export) Rules ---
-keep class org.apache.poi.** { *; }
-dontwarn org.apache.poi.**
-keep class org.apache.xmlbeans.** { *; }
-dontwarn org.apache.xmlbeans.**
-keep class org.openxmlformats.** { *; }
-dontwarn org.openxmlformats.**

# --- Ignore Missing Optional Dependencies ---
-dontwarn aQute.bnd.annotation.spi.**
-dontwarn java.awt.Shape
-dontwarn javax.xml.stream.**
-dontwarn net.sf.saxon.**
-dontwarn org.osgi.framework.**
-dontwarn org.apache.logging.log4j.**
-dontwarn com.graphbuilder.**

# --- Retrofit Rules ---
-keep class retrofit2.** { *; }
-keep interface retrofit2.** { *; }
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

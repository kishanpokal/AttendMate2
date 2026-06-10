# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# --- AttendMate Custom ProGuard Rules ---

# Prevent R8 from stripping WebView JavascriptInterface methods
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep Firestore deserializable models to prevent reflection mapping issues
-keepclassmembers class * {
    @com.google.firebase.firestore.PropertyName <fields>;
    @com.google.firebase.firestore.PropertyName <methods>;
}

# Keep all package-specific models and data classes used for serialization/deserialization
-keep class com.kishan.attendmate.ui.ai.** { *; }
-keep class com.kishan.attendmate.ui.friends.** { *; }
-keep class com.kishan.attendmate.ui.settings.** { *; }
-keep class com.kishan.attendmate.ui.timetable.setup.** { *; }
-keep class com.kishan.attendmate.ui.subjects.** { *; }
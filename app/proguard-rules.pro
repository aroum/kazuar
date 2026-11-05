# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /home/iwo/android-sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Inherited LatinIME JNI, reflection and component rules.
# Keep classes and methods that have the @UsedForTesting annotation
-keep @io.github.aroum.kazuar.inputmethod.annotations.UsedForTesting class *
-keepclassmembers class * {
    @io.github.aroum.kazuar.inputmethod.annotations.UsedForTesting *;
}

# Keep classes and methods that have the @ExternallyReferenced annotation
-keep @io.github.aroum.kazuar.inputmethod.annotations.ExternallyReferenced class *
-keepclassmembers class * {
    @io.github.aroum.kazuar.inputmethod.annotations.ExternallyReferenced *;
}

# Keep native methods
-keepclassmembers class * {
    native <methods>;
}

# Keep classes that are used as a parameter type of methods that are also marked as keep
# to preserve changing those methods' signature.
-keep class io.github.aroum.kazuar.inputmethod.latin.AssetFileAddress
-keep class io.github.aroum.kazuar.inputmethod.latin.Dictionary
-keep class io.github.aroum.kazuar.inputmethod.latin.NgramContext
-keep class io.github.aroum.kazuar.inputmethod.latin.makedict.ProbabilityInfo
-keep class io.github.aroum.kazuar.inputmethod.latin.utils.LanguageModelParam

# Keep classes and members referenced by JNI and core keyboard logic
-keep class io.github.aroum.kazuar.inputmethod.keyboard.KeyboardLayout { *; }
-keep class io.github.aroum.kazuar.inputmethod.keyboard.ProximityInfo { *; }
-keep class io.github.aroum.kazuar.inputmethod.latin.BinaryDictionary { *; }
-keep class io.github.aroum.kazuar.inputmethod.latin.utils.BinaryDictionaryUtils { *; }
-keep class io.github.aroum.kazuar.inputmethod.latin.DicTraverseSession { *; }
-keep class io.github.aroum.kazuar.inputmethod.latin.utils.WordInputEventForPersonalization { *; }
-keep class io.github.aroum.kazuar.inputmethod.latin.ExpandableBinaryDictionary { *; }
-keep class io.github.aroum.kazuar.inputmethod.latin.personalization.UserHistoryDictionary { *; }
-keep class io.github.aroum.kazuar.inputmethod.latin.UserBinaryDictionary { *; }
-keep class io.github.aroum.kazuar.inputmethod.latin.ContactsBinaryDictionary { *; }

# Keep InputMethodService and Activity components
-keep public class * extends android.inputmethodservice.InputMethodService
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# Keep preference classes used via XML reflection
-keep public class * extends androidx.preference.Preference
-keep public class * extends android.preference.Preference

# Strip debug logging calls in optimized builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}

# Disable obfuscation to keep stack traces readable and prevent reflection/JNI mismatches
-dontobfuscate

# Keep all Kazuar classes and members
-keep class io.github.aroum.kazuar.** { *; }

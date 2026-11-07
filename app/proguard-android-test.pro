# AndroidX Test references build-time Error Prone annotations (CLASS retention).
# They have no Android runtime implementation or behavior. Do not bundle the
# compiler annotation artifact: its other annotations depend on javax.lang.model.
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.MustBeClosed

# Instrumentation discovers JUnit tests through annotations/reflection.
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keep class io.github.aroum.kazuar.inputmethod.latin.settings.ShrunkApkSmokeTest { *; }

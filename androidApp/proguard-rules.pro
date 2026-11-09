# QuickJS JNI bridge: quickjs_jni.c resolves the class and these members BY NAME
# (FindClass / GetStaticMethodID / the exception ctor) — renaming any of them breaks
# every engine callback at runtime.
-keep class io.ymusic.quickjs.** { *; }
-keepclassmembers class io.ymusic.quickjs.** { *; }
-keep class io.ymusic.quickjs.QuickJsException { <init>(java.lang.String); }

# MSE native bindings & audio engine
# mse_host_jni.c & audio_fx_jni.c export JNI functions under io.ymusic.mse.*
# R8 must NEVER rename, strip or optimize away any MSE native methods or classes.
-keep class io.ymusic.mse.** { *; }
-keepclassmembers class io.ymusic.mse.** { *; }

# Keep all native methods across the entire app
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# WebView page JS calls bridge methods through addJavascriptInterface.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# kotlinx.serialization: plugin-generated serializers are looked up via the Companion.
# The library ships consumer rules, but the named-companion lookup needs the app side too.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$Companion Companion;
}
-keep,allowshrinking,allowobfuscation class <2>$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}

# Sentry events must map back to source lines; the file name is renamed, not dropped.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Sentry titles and fingerprints events by the client-side exception type, and the telemetry
# beforeSend matches TrailMark by name: unrenamed, and pinned against R8 class merging folding
# unrelated exceptions into one type. Unused throwables are still removed.
-keepnames class * extends java.lang.Throwable

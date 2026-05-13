# NanoHTTPD — uses reflection on response/status classes.
-keep class fi.iki.elonen.** { *; }
-dontwarn javax.annotation.Nullable
-dontwarn javax.annotation.concurrent.GuardedBy

# ZXing — reflection-free for our usage, but keep enums + encoder classes to be safe.
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# Timber — DebugTree uses stack walking to infer tags.
-dontwarn timber.log.**

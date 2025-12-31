# Add project specific ProGuard rules here.

# --- WorkManager ---
# WorkManager instantiates workers via reflection; keep all worker subclasses and their constructors.
-keep class * extends androidx.work.ListenableWorker {
    <init>(...);
}

# --- JGit / SLF4J ---
# JGit references some Java SE APIs which are not present on Android; these codepaths are not used in-app.
-dontwarn java.lang.ProcessHandle
-dontwarn java.lang.management.ManagementFactory
-dontwarn javax.management.**
-dontwarn org.ietf.jgss.**
-dontwarn org.slf4j.impl.StaticLoggerBinder

# --- J2V8 ---
# J2V8 uses JNI and expects stable class/method names; keep it from being obfuscated/shrunk in release.
-keep class com.eclipsesource.v8.** { *; }
-dontwarn com.eclipsesource.v8.**

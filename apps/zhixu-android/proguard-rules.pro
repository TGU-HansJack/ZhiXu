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

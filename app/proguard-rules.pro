# Release obfuscation policy. The default optimize file enables R8 shrinking
# and optimization; these rules make naming and debug metadata stricter.
-allowaccessmodification
-overloadaggressively
-repackageclasses ''
-adaptclassstrings

# JSch resolves SSH algorithms by class name strings. Keep only the bundled
# JSch library readable; application SSH wrappers and all other code still
# follow the normal release obfuscation policy.
-keep class com.jcraft.jsch.** { *; }

# AIDL-generated IPC stubs must keep their names so that cross-process
# Binder calls can resolve the interface, Stub and Proxy classes at runtime.
-keep class cn.lineai.ipc.** { *; }
-keep class * extends android.os.IInterface
-keepclassmembers class * extends android.os.IInterface {
    public static ** asInterface(android.os.IBinder);
    public static ** castDefaultImpl(android.os.IBinder);
}

# Optional JSch integrations that are not packaged on Android.
-dontwarn com.sun.jna.**
-dontwarn org.apache.logging.log4j.**
-dontwarn org.bouncycastle.**
-dontwarn org.ietf.jgss.**
-dontwarn org.newsclub.net.unix.**
-dontwarn org.slf4j.**

-obfuscationdictionary build/generated/r8/obfuscation-dictionary.txt
-classobfuscationdictionary build/generated/r8/obfuscation-dictionary.txt
-packageobfuscationdictionary build/generated/r8/obfuscation-dictionary.txt

# Do not keep SourceFile or LineNumberTable. Keep only metadata commonly needed
# by Java libraries and runtime annotations.
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod

# Local-model keep rules live in engine-local/consumer-rules.pro (JNI entry points and
# callback method names) and runtime/consumer-rules.pro (reflective bridge class name).

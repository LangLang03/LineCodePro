# Release obfuscation policy. The default optimize file enables R8 shrinking
# and optimization; these rules align with the LineCode app's filter set
# and only add entries required by the IPC stub / Service entry points.

# AIDL-generated IPC stubs must keep their names so that cross-process
# Binder calls can resolve the interface, Stub and Proxy classes at runtime.
# These mirror the rules in app/proguard-rules.pro.
-keep class cn.lineai.ipc.** { *; }
-keep class * extends android.os.IInterface
-keepclassmembers class * extends android.os.IInterface {
    public static ** asInterface(android.os.IBinder);
    public static ** castDefaultImpl(android.os.IBinder);
}

# Allow access modification and aggressive overload for tighter obfuscation.
-allowaccessmodification
-overloadaggressively
-repackageclasses ''
-adaptclassstrings

# Do not keep SourceFile or LineNumberTable. Keep only metadata commonly needed
# by Java libraries and runtime annotations.
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod

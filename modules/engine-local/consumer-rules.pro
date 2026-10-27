# JNI entry points are resolved by Java_<class>_<method>, so the class and native method
# names must survive R8.
-keepclasseswithmembernames class cn.lineai.engine.local.NativeLlamaEngine {
    native <methods>;
}

# The native side looks these callback methods up by name.
-keepclassmembers class * implements cn.lineai.engine.local.NativeLlamaEngine$Callback {
    public void onPiece(java.lang.String);
    public void onLoadProgress(int);
    public boolean isCancelled();
}

-dontwarn io.github.libxposed.annotation.**
-adaptresourcefilecontents META-INF/xposed/java_init.list
-keep,allowoptimization,allowobfuscation public class * extends io.github.libxposed.api.XposedModule {
    public <init>();
}

# DexKit's JNI entry is loaded by its original class name.
-keep class org.luckypray.dexkit.** { *; }

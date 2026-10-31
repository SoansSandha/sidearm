# From the libxposed API README: keep module entry classes and rewrite
# META-INF/xposed/java_init.list if R8 renames them.
-dontwarn io.github.libxposed.annotation.**
-adaptresourcefilecontents META-INF/xposed/java_init.list
-keep,allowoptimization,allowobfuscation public class * extends io.github.libxposed.api.XposedModule {
    public <init>();
}

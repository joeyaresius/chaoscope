# Keep JNI bridge — the cpp side resolves these by FQN at runtime.
-keep class com.chaoscope.ChaoscopeEngine { *; }
-keepclassmembers class com.chaoscope.ChaoscopeEngine { native <methods>; }

# Keep enums whose ordinals are passed to native code (AttractorType, PaletteType, etc.).
-keepclassmembers enum com.chaoscope.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

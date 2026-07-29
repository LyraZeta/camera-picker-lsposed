# The Xposed entry point is discovered from assets/xposed_init.
# Keep all module classes available to the runtime hook loader.
-keep class com.lyra.camerapicker.** { *; }

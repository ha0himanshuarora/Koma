# Koma uses no reflection and no serialisation library, so R8 defaults are enough.
# Manifest components (activity, notification listener, accessibility service) are kept automatically.
-dontwarn kotlin.**
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

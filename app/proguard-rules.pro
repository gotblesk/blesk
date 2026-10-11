# Navigation keys and the appearance model are restored through kotlinx.serialization.
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

# Back stack entries are saved and restored by class name.
-keep class * implements androidx.navigation3.runtime.NavKey { *; }

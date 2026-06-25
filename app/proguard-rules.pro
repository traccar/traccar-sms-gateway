# EventBus
-keepattributes *Annotation*
-keepclassmembers class ** {
    @org.greenrobot.eventbus.Subscribe <methods>;
}
-keep enum org.greenrobot.eventbus.ThreadMode { *; }

# Keep `Companion` object fields of serializable classes.
# This avoids serializer lookup through `getDeclaredClasses` as done for named companion objects.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

# Keep `serializer()` on companion objects (both default and named) of serializable classes.
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep `INSTANCE.serializer()` of serializable objects.
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# Strip Fossify Commons' anti-fork "fake version" nag, which fires because our package is
# not org.fossify.*. Marking the void check side-effect-free lets R8 drop every call to it.
-assumenosideeffects class org.fossify.commons.compose.extensions.ActivityExtensionsKt {
    void fakeVersionCheck(android.content.Context, kotlin.jvm.functions.Function0);
}

# Gson
-keep class org.fossify.commons.models.SimpleContact { *; }
-keep class org.fossify.messages.models.Attachment { *; }
-keep class org.fossify.messages.models.MessageAttachment { *; }

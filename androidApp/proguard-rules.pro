# Gym — R8 rules for the release build.

# Firestore documents are (de)serialised with kotlinx.serialization via GitLive; keep the generated
# serializers and the model classes' shape.
-keepattributes *Annotation*, InnerClasses, Signature
-keep,includedescriptorclasses class com.gym.app.**$$serializer { *; }
-keepclassmembers class com.gym.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.gym.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep GitLive's Firebase wrappers intact (they bridge to the Android SDK by name in places).
-keep class dev.gitlive.firebase.** { *; }
-dontwarn dev.gitlive.firebase.**

# Ktor (Coil's network engine) references optional JVM classes.
-dontwarn org.slf4j.**
-dontwarn java.lang.management.**

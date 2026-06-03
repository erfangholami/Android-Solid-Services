# Android Solid Services — Shared consumer R8/ProGuard rules.
# These ship inside the AAR and are applied when a consuming app minifies.

# Parcelable CREATOR fields must survive minification so AIDL/IPC unmarshalling works.
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# The api module reconstructs resource types reflectively (SolidResourceParser);
# keep their constructors so reflective instantiation does not break under R8.
-keep class com.erfangholami.androidsolidservices.shared.model.resource.** {
    <init>(...);
}

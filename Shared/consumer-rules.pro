# Android Solid Services — Shared consumer R8/ProGuard rules.
# Packaged into the AAR (consumerProguardFiles) and merged into a consuming app's R8 run.
# The library is not minified itself, so anything a consumer needs must live here, not in
# proguard-rules.pro.

# @Parcelize IPC models cross the process boundary; AIDL marshalling resolves the generated
# CREATOR field, so it must survive minification. Scoped to Shared's model package rather than
# every Parcelable in the consumer app.
-keepclassmembers class com.erfangholami.androidsolidservices.shared.model.** implements android.os.Parcelable {
    public static final ** CREATOR;
}

# Resource types are reconstructed reflectively via Class.getConstructor(...).newInstance(...) by
# api's SolidResourceParser (in the ASS app process) and by client's SolidResourceClient
# (reconstructRdf/reconstructNonRdf, in a third-party process). Keep the canonical constructors
# on every RDF and non-RDF subtype so reflective construction survives R8. The `extends` form
# covers Shared's own models (SolidRDFResource, SolidContainer, SolidNonRDFResource), the
# api-module subtypes (CatalogRDF, WebId, *RDF, SolidACLResource, …), and any resource subtype a
# downstream SDK consumer defines — all of which a package-scoped rule would miss.
-keepclassmembers class * extends com.erfangholami.androidsolidservices.shared.model.resource.RDFResource {
    <init>(java.net.URI, java.lang.String, java.util.List, com.erfangholami.androidsolidservices.shared.http.SolidHeaders);
}
-keepclassmembers class * extends com.erfangholami.androidsolidservices.shared.model.resource.NonRDFResource {
    <init>(java.net.URI, java.lang.String, java.io.InputStream);
    <init>(java.net.URI, java.lang.String, com.erfangholami.androidsolidservices.shared.http.SolidHeaders, java.io.InputStream);
}

# titanium-json-ld parses JSON-LD through the jakarta.json (JSON-P) SPI. The Glassfish provider
# bundles no META-INF/services entry usable on Android, so jakarta.json.spi.JsonProvider.provider()
# falls back to Class.forName("org.glassfish.json.JsonProviderImpl") — a string R8 cannot trace.
# Keep the provider and its no-arg constructor so JSON-LD parsing works in a minified build.
-keep class org.glassfish.json.JsonProviderImpl {
    public <init>();
}

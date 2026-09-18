# Android Solid Services — Shared consumer R8/ProGuard rules.
# Packaged into the AAR (consumerProguardFiles) and merged into a consuming app's R8 run.
# The library is not minified itself, so anything a consumer needs must live here, not in
# proguard-rules.pro.

# @Parcelize IPC models cross the process boundary. AIDL marshalling resolves the generated
# CREATOR field, and AGP's default ProGuard files — which every AGP release build lists — keep
# `public static final ** CREATOR` on every Parcelable, so the field needs no rule here (a
# package-scoped copy was subsumed by that default and added nothing).
#
# The models' NAMES have to survive too, which keeping CREATOR does not achieve.
#
# A nested Parcelable is written with Parcel.writeParcelable, which stamps getClass().getName()
# into the parcel for the reader to resolve. Minified, that name is the obfuscated one: the ASS app
# wrote "xt5" for WacAllow inside SolidMetadata, and "by4" for SolidMetadata inside
# SolidSourceReference. A third-party app has no such classes, so unmarshalling threw
# BadParcelableException — meaning every release build broke `head`, `headPublic`, `readContainer`
# and enriched `listContainer` for every consumer, while debug builds worked perfectly.
#
# The same stamping happens for every answer a host sends back: the binders envelope each result
# in a Bundle (IpcEnvelope, putParcelable / putParcelableArrayList), and a Bundle writes the
# RUNTIME class name of whatever it holds — in the writer's process, so the host's build decides
# the name a third-party app has to resolve. Today every enveloped runtime type lives under
# shared.model.**, but the RDF codec types under shared.rdf.** are Parcelable too (they extend
# SolidRDFResource), and a future verb could hand one of those, or any other library Parcelable,
# to the envelope. The rule therefore spans the whole library namespace rather than one package:
# a wider match costs nothing but the names themselves, while a missed class fails only in
# minified builds, as above.
#
# -keepnames (keep, but still allow shrinking) so unused models can still be removed. Measured in
# a minified host app this pins class names only — no fields, no methods.
-keepnames class com.erfangholami.androidsolidservices.** implements android.os.Parcelable

# Resource types are reconstructed reflectively via Class.getConstructor(...).newInstance(...) by
# api's SolidResourceParser (in the ASS app process) and by client's SolidResourceClient
# (reconstructRdf/reconstructNonRdf, in a third-party process). Keep the canonical constructors
# on every RDF and non-RDF subtype so reflective construction survives R8. The `extends` form
# covers Shared's own models (SolidRDFResource, SolidContainer, SolidNonRDFResource), the
# api-module subtypes (CatalogRDF, WebId, *RDF, SolidACLResource, …), and any resource subtype a
# downstream SDK consumer defines — all of which a package-scoped rule would miss; `extends` does
# not match the named class itself, so the base classes get their own rules.
# The signatures must mirror the getConstructor(...) call sites exactly — the identifier parameter
# is java.lang.String, and the non-RDF lookup tries all three parameter orders. A signature that
# drifts from the call sites matches nothing, R8 strips the constructors, and every reflective
# read fails with NoSuchMethodException in minified builds only.
-keepclassmembers class com.erfangholami.androidsolidservices.shared.model.resource.RDFResource {
    <init>(java.lang.String, java.lang.String, java.util.List, com.erfangholami.androidsolidservices.shared.http.SolidHeaders);
}
-keepclassmembers class * extends com.erfangholami.androidsolidservices.shared.model.resource.RDFResource {
    <init>(java.lang.String, java.lang.String, java.util.List, com.erfangholami.androidsolidservices.shared.http.SolidHeaders);
}
-keepclassmembers class com.erfangholami.androidsolidservices.shared.model.resource.NonRDFResource {
    <init>(java.lang.String, java.lang.String, java.io.InputStream);
    <init>(java.lang.String, java.lang.String, com.erfangholami.androidsolidservices.shared.http.SolidHeaders, java.io.InputStream);
    <init>(java.lang.String, java.lang.String, java.io.InputStream, com.erfangholami.androidsolidservices.shared.http.SolidHeaders);
}
-keepclassmembers class * extends com.erfangholami.androidsolidservices.shared.model.resource.NonRDFResource {
    <init>(java.lang.String, java.lang.String, java.io.InputStream);
    <init>(java.lang.String, java.lang.String, com.erfangholami.androidsolidservices.shared.http.SolidHeaders, java.io.InputStream);
    <init>(java.lang.String, java.lang.String, java.io.InputStream, com.erfangholami.androidsolidservices.shared.http.SolidHeaders);
}

# titanium-json-ld parses JSON-LD through the jakarta.json (JSON-P) SPI. The Glassfish provider
# bundles no META-INF/services entry usable on Android, so jakarta.json.spi.JsonProvider.provider()
# falls back to Class.forName("org.glassfish.json.JsonProviderImpl") — a string R8 cannot trace.
# Keep the provider and its no-arg constructor so JSON-LD parsing works in a minified build.
-keep class org.glassfish.json.JsonProviderImpl {
    public <init>();
}

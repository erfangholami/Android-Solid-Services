# Android Solid Services — client consumer R8/ProGuard rules.
# Intentionally empty: a minified downstream consumer of `client` needs no rules from here.
#  - AIDL Stub/asInterface/callback Stubs are referenced statically (kept by use), not reflectively.
#  - The only reflection — SolidResourceClient.reconstructRdf/reconstructNonRdf — builds
#    RDFResource/NonRDFResource subtypes; their constructor keeps are owned by Shared's
#    consumer-rules.pro and ship transitively (client depends on Shared).
#  - All marshalled Parcelables are covered by Shared: AGP's default ProGuard file keeps their
#    CREATOR fields, and Shared's -keepnames rule keeps the name of every Parcelable in the
#    library namespace — the name a Bundle envelope stamps for this process to resolve.
#  - SignInButton relies on Compose's own consumer rules.
# Add rules here only if client itself gains a reflective or serialized surface.

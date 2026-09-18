# Android Solid Services — host consumer R8/ProGuard rules.
# Intentionally empty: a minified host app needs no rules from here.
#  - The AIDL Stubs the binders extend are referenced statically (kept by use).
#  - Every marshalled Parcelable is covered by Shared's consumer rules, which ship transitively
#    (host depends on Shared): AGP's default ProGuard file keeps the CREATOR fields, and Shared's
#    -keepnames rule keeps the name of every Parcelable in the library namespace — the name a
#    Bundle envelope stamps for the receiving app to resolve.
#  - The grant store serializes shared.model.grant.** with kotlinx.serialization, whose own
#    embedded rules keep the generated serializers.
# Add rules here only if host itself gains a reflective or serialized surface.

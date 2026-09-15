# Android Solid Services — host consumer R8/ProGuard rules.
# Intentionally empty: a minified host app needs no rules from here.
#  - The AIDL Stubs the binders extend are referenced statically (kept by use).
#  - Every marshalled Parcelable lives under shared.model.** and is covered by Shared's
#    consumer rules, which ship transitively (host depends on Shared).
#  - The grant store serializes shared.model.grant.** with kotlinx.serialization, whose own
#    embedded rules keep the generated serializers.
# Add rules here only if host itself gains a reflective or serialized surface.

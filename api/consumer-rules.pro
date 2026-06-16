# Android Solid Services — api consumer R8/ProGuard rules.
# Packaged into the AAR (consumerProguardFiles) and merged into a consuming app's R8 run.
# The library is not minified itself, so anything a consumer needs must live here, not in
# proguard-rules.pro.

# JJWT (DPoP/ID-token signing & verification) resolves its implementation classes reflectively
# by name, two ways R8 cannot trace:
#   1. Codecs/serializers via service files read by JJWT's own loader (Deflate/Gzip compression,
#      the orgjson Serializer/Deserializer).
#   2. The algorithm registries and builders via direct Classes.newInstance("io.jsonwebtoken.impl…")
#      from the Jwts factory — e.g. Jwts.SIG -> impl.security.StandardSecureDigestAlgorithms
#      (DPoP ES256 signing) and Jwts.builder() -> impl.DefaultJwtBuilder. These have NO service
#      file, so keeping only the service-file classes is not enough — the whole impl tree must
#      survive shrinking, or DPoP signing crashes with UnknownClassException at runtime.
-keep class io.jsonwebtoken.impl.** { *; }
-keep class io.jsonwebtoken.orgjson.io.OrgJsonSerializer { <init>(); }
-keep class io.jsonwebtoken.orgjson.io.OrgJsonDeserializer { <init>(); }

# JJWT looks up an optional BouncyCastle provider by name
# (Class.forName("org.bouncycastle.jce.provider.BouncyCastleProvider")). BouncyCastle is not on
# the runtime classpath here — DPoP signing uses the platform JCA providers / Android Keystore —
# and there is no hard bytecode reference, only the reflective string. This -dontwarn is
# precautionary, keeping the build quiet should a future JJWT revision add a compile-time
# BouncyCastle reference. The previous -keep org.bouncycastle.** rules matched no classes and
# were removed.
-dontwarn org.bouncycastle.**

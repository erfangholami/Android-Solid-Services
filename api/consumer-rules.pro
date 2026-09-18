# Android Solid Services — api consumer R8/ProGuard rules.
# Packaged into the AAR (consumerProguardFiles) and merged into a consuming app's R8 run.
# The library is not minified itself, so anything a consumer needs must live here, not in
# proguard-rules.pro.

# JJWT (DPoP/ID-token signing & verification) resolves implementation classes reflectively by
# name, three ways R8 cannot trace:
#   1. The jjwt-api factories instantiate their impl counterparts via
#      Classes.newInstance("io.jsonwebtoken.impl…") — Jwts.builder() / parser() / header() /
#      claims() go through the nested *$Supplier classes, and the algorithm registries
#      (Jwts.SIG for DPoP ES256, Jwts.ENC/KEY/ZIP, Jwks.CRV/HASH/OP) through the Standard*
#      classes. These have NO service file. Instantiation only needs the class name and the
#      no-arg constructor to survive — every other member is reached statically from those
#      roots, so R8 traces and shrinks the rest of the impl tree. jjwt-api 0.13.0 carries exactly
#      19 io.jsonwebtoken.impl class-name literals (jjwt-impl and jjwt-orgjson carry none): the 17
#      kept here by constructor, plus the two bridges under point 3. Revisit the list when jjwt is
#      bumped — grep the jjwt-api jar for dotted `io.jsonwebtoken.impl.` strings. A package-wide
#      `io.jsonwebtoken.impl.** { <init>(...); }` rule pinned 350 classes for the same effect.
#   2. Codecs/serializers via META-INF/services files read through ServiceLoader, so their names
#      appear in no jar's bytecode: the two compression codecs listed in jjwt-impl's
#      META-INF/services/io.jsonwebtoken.CompressionCodec, and the orgjson Serializer/Deserializer.
#   3. Static bridge methods via Classes.invokeStatic on JwksBridge/KeysBridge, which must keep
#      their static members, not just constructors.
-keep class io.jsonwebtoken.impl.DefaultClaimsBuilder$Supplier { <init>(); }
-keep class io.jsonwebtoken.impl.DefaultJwtBuilder$Supplier { <init>(); }
-keep class io.jsonwebtoken.impl.DefaultJwtHeaderBuilder$Supplier { <init>(); }
-keep class io.jsonwebtoken.impl.DefaultJwtParserBuilder$Supplier { <init>(); }
-keep class io.jsonwebtoken.impl.io.StandardCompressionAlgorithms { <init>(); }
-keep class io.jsonwebtoken.impl.security.DefaultDynamicJwkBuilder$Supplier { <init>(); }
-keep class io.jsonwebtoken.impl.security.DefaultJwkParserBuilder$Supplier { <init>(); }
-keep class io.jsonwebtoken.impl.security.DefaultJwkSetBuilder$Supplier { <init>(); }
-keep class io.jsonwebtoken.impl.security.DefaultJwkSetParserBuilder$Supplier { <init>(); }
-keep class io.jsonwebtoken.impl.security.DefaultKeyOperationBuilder$Supplier { <init>(); }
-keep class io.jsonwebtoken.impl.security.DefaultKeyOperationPolicyBuilder$Supplier { <init>(); }
-keep class io.jsonwebtoken.impl.security.StandardCurves { <init>(); }
-keep class io.jsonwebtoken.impl.security.StandardEncryptionAlgorithms { <init>(); }
-keep class io.jsonwebtoken.impl.security.StandardHashAlgorithms { <init>(); }
-keep class io.jsonwebtoken.impl.security.StandardKeyAlgorithms { <init>(); }
-keep class io.jsonwebtoken.impl.security.StandardKeyOperations { <init>(); }
-keep class io.jsonwebtoken.impl.security.StandardSecureDigestAlgorithms { <init>(); }
-keep class io.jsonwebtoken.impl.compression.DeflateCompressionAlgorithm { <init>(); }
-keep class io.jsonwebtoken.impl.compression.GzipCompressionAlgorithm { <init>(); }
-keep class io.jsonwebtoken.impl.security.JwksBridge { public static <methods>; }
-keep class io.jsonwebtoken.impl.security.KeysBridge { public static <methods>; }
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

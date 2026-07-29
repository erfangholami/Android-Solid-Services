# Android Solid Services — app R8/ProGuard rules. This is the only minified module
# (isMinifyEnabled = true); the three libraries ship their rules via consumer-rules.pro.
#
# The app needs no keep rules of its own for correctness:
#  - kotlinx.serialization is covered by the R8 rules embedded in kotlinx-serialization-core.
#  - The reflective resource construction (SolidResourceParser) targets Shared types and is kept
#    by Shared's consumer-rules.pro, which merges in through the api/client AAR dependencies.
#  - Bound services and activities are kept via the AndroidManifest, Hilt entry points via Hilt's
#    own rules, and WorkManager/Compose/AppAuth/okhttp ship their own consumer rules.
#
# Keep line-number info so release crash reports deobfuscate against the generated mapping.txt,
# while still obfuscating the original .kt file names. Remove these two lines if deobfuscatable
# stack traces are not needed. Crashlytics requires both to symbolicate uploaded reports.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Crashlytics groups non-fatals by exception class name, so keep the names of the exception types
# the SDK reports (SharingException, SolidException and the rest) readable in the console.
# Scoped to this project and -keepnames rather than -keep: an unqualified rule also pins ~260
# unrelated library exceptions, and -keep would block shrinking of the ones that go unused.
-keepnames class com.erfangholami.androidsolidservices.** extends java.lang.Exception

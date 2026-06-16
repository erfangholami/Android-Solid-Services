# Shared is published as an AAR and is not minified on its own (isMinifyEnabled = false), so
# the rules in this file are never applied to anything.
#
# R8/ProGuard rules that downstream consumers need when THEY minify belong in
# consumer-rules.pro — that file is packaged into the AAR via consumerProguardFiles and merged
# into the consumer app's R8 configuration. Keep this file empty unless Shared itself is ever
# switched to isMinifyEnabled = true.

// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {

    repositories {
        google()
        mavenCentral()
        maven(url = uri("https://jitpack.io"))
    }
}

plugins {
    alias(libs.plugins.jetbrains.kotlin.jvm) apply false
    alias(libs.plugins.jetbrains.kotlin.parcelize) apply false
    alias(libs.plugins.jetbrains.kotlin.serialization) apply false
    alias(libs.plugins.jetbrains.kotlin.compose.compiler) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.google.hilt.android) apply false
    alias(libs.plugins.vanniktech.maven.publish) apply false
}

// No public-API guard is configured. binary-compatibility-validator was removed because it
// registers no tasks under AGP 9: it hooks on the standalone Kotlin Android plugin, which AGP
// replaces with KotlinBaseApiPlugin and refuses to let you apply. KGP's own ABI validation is
// blocked by the same gap — finalizeAndroidVariant() needs a KotlinTarget that AGP-driven
// Kotlin never registers, so its dumps come out empty.

tasks.register("clean", Delete::class) {
    delete(project.layout.buildDirectory)
}
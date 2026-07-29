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
    alias(libs.plugins.diffplug.spotless) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.jetbrains.dokka)
}

// API reference for the three published libraries, aggregated into one site.
// `app` is excluded: it ships no public API.
//   ./gradlew dokkaGeneratePublicationHtml   -> build/dokka/html
dependencies {
    dokka(project(":Shared"))
    dokka(project(":api"))
    dokka(project(":client"))
}

dokka {
    moduleName.set("Android Solid Services")
}

// Stages the generated API reference where MkDocs expects it (mkdocs.yml already navigates to
// api/index.html). Sync rather than Copy so removed declarations do not linger. The output is
// generated, not committed — see .gitignore.
tasks.register<Sync>("dokkaToDocs") {
    description = "Generates the API reference into docs/api for the MkDocs site."
    group = "documentation"
    from(tasks.named("dokkaGeneratePublicationHtml"))
    into(layout.projectDirectory.dir("docs/api"))
}

// No public-API guard is configured. binary-compatibility-validator was removed because it
// registers no tasks under AGP 9: it hooks on the standalone Kotlin Android plugin, which AGP
// replaces with KotlinBaseApiPlugin and refuses to let you apply. KGP's own ABI validation is
// blocked by the same gap — finalizeAndroidVariant() needs a KotlinTarget that AGP-driven
// Kotlin never registers, so its dumps come out empty.

val ktlintVersion = libs.versions.ktlint.get()
val detektConfigFile = rootProject.files("config/detekt/detekt.yml")
val editorConfigPath = rootProject.file(".editorconfig").absolutePath

allprojects {
    apply(plugin = "com.diffplug.spotless")
    apply(plugin = "io.gitlab.arturbosch.detekt")

    extensions.configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        kotlin {
            target("src/**/*.kt")
            targetExclude("**/build/**")
            ktlint(ktlintVersion)
                .setEditorConfigPath(editorConfigPath)
        }
        kotlinGradle {
            target("*.gradle.kts")
            ktlint(ktlintVersion)
                .setEditorConfigPath(editorConfigPath)
        }
    }

    extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        config.setFrom(detektConfigFile)
        buildUponDefaultConfig = true
        parallel = true
        basePath = rootProject.projectDir.absolutePath
        // Pre-existing findings are recorded per module so only new ones fail the build.
        baseline = file("detekt-baseline.xml")
    }

    tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
        jvmTarget = "17"
        reports {
            sarif.required.set(true)
            html.required.set(true)
            xml.required.set(false)
            txt.required.set(false)
            md.required.set(false)
        }
    }
}

// Spotless pulls in Gradle's `base` plugin, which already registers an equivalent
// `clean` in every project, so this build no longer registers one by hand.

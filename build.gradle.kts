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
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
    alias(libs.plugins.diffplug.spotless) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.jetbrains.dokka)
}

// The version is derived from git, so releasing is tagging — nothing is edited by hand.
// On the exact tag `v0.6.1` this yields versionName 0.6.1 and versionCode 601
// (MAJOR·10000 + MINOR·100 + PATCH — monotonic as long as versions ascend, which the release
// workflow enforces); between tags the name carries the distance and commit
// (`0.6.1-3-g1a2b3c4`, `-dirty` when the tree is), while the code stays at the base tag's.
// The Maven coordinates in Shared/api/client read the same values, so all five version sites
// that used to be hand-written now agree by construction. `-PassVersion=X.Y.Z` overrides the
// derivation for builders without a git checkout.
val describedVersion: String = runCatching {
    providers.gradleProperty("assVersion").orElse(
        providers.exec {
            // Pinned to this repo: in a composite build (the client sample includes this one)
            // the process working directory is the including build's, and describing the wrong
            // repository yields the wrong — or no — version.
            workingDir = rootDir
            commandLine("git", "describe", "--tags", "--match", "v[0-9]*", "--dirty")
            isIgnoreExitValue = true
        }.standardOutput.asText.map(String::trim),
    ).getOrElse("")
}.getOrDefault("")

val assVersionName: String = describedVersion.removePrefix("v").ifEmpty { "0.0.0-unknown" }

// Never below 1: AGP rejects versionCode 0, which is what the fallback name would produce.
val assVersionCode: Int = (
    Regex("""^(\d+)\.(\d+)\.(\d+)""").find(assVersionName)
        ?.destructured
        ?.let { (major, minor, patch) ->
            require(minor.toInt() < 100 && patch.toInt() < 100) {
                "versionCode packs minor and patch into two digits each; $assVersionName does not fit"
            }
            major.toInt() * 10_000 + minor.toInt() * 100 + patch.toInt()
        }
        ?: 1
    ).coerceAtLeast(1)

extra["assVersionName"] = assVersionName
extra["assVersionCode"] = assVersionCode

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

// The version the docs quote is the last *released* one, so the describe suffix is dropped:
// between tags `0.6.1-16-g3f29ffb` still means "0.6.1 is what you can depend on". On a tag the
// two are already the same.
val docsVersion: String =
    Regex("""^\d+\.\d+\.\d+""").find(assVersionName)?.value ?: assVersionName

// Writes the dependency blocks the docs include with `--8<--`, so no version is typed by hand.
// Three sites drifted apart the last time they were (getting-started said 0.6.0, the README
// 0.6.1, the artifacts 0.7.0); generating them from the same value the artifacts publish under
// is what stops that recurring. Committed rather than gitignored, so `mkdocs serve` works on a
// fresh clone without running Gradle first — CI regenerates them before every deploy.
tasks.register("docsIncludes") {
    description = "Generates the version-bearing MkDocs includes into docs/_includes."
    group = "documentation"

    val includesDir = layout.projectDirectory.dir("docs/_includes")
    val version = docsVersion
    val generated = listOf("dependency-client.md", "dependency-api.md", "version.md")

    inputs.property("version", version)
    // The generated files, not the directory: `abbreviations.md` is hand-written and lives here
    // too, and declaring the directory would put it in reach of Gradle's stale-output cleanup.
    generated.forEach { outputs.file(includesDir.file(it)) }

    doLast {
        val dir = includesDir.asFile
        dir.mkdirs()

        fun dependencyBlock(artifact: String) =
            """
            ```kotlin title="build.gradle.kts"
            dependencies {
                implementation("com.erfangholami.androidsolidservices:$artifact:$version")
            }
            ```
            """.trimIndent() + "\n"

        dir.resolve("dependency-client.md").writeText(dependencyBlock("client"))
        dir.resolve("dependency-api.md").writeText(dependencyBlock("api"))
        dir.resolve("version.md").writeText(version)
    }
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

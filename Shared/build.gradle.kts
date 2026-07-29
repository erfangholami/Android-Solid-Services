import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.SourcesJar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.dokka)
    alias(libs.plugins.jetbrains.kotlin.serialization)
    alias(libs.plugins.vanniktech.maven.publish)
    id("kotlin-parcelize")
    `maven-publish`
}

android {
    namespace = "com.erfangholami.androidsolidservices.shared"
    compileSdk = 37

    lint {
        // AGP 9 always generates every report format; only the failure policy is settable.
        abortOnError = true
    }

    defaultConfig {
        minSdk = 26

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        aidl = true
    }
    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

kotlin {
    explicitApi()
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
    jvmToolchain(17)
}

dependencies {

    implementation(libs.jetbrains.kotlinx.serialization.json)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)

    api(libs.openid.appauth)

    // Internal RDF (JSON-LD) codec only — NOT exposed on the public API (resource models use
    // String content-type + SolidHeaders), so these stay off consumers' compile classpath.
    implementation(libs.titanium.json.ld.jre8)
    implementation(libs.glassfish.jakarta.json)
}

mavenPublishing {
    configure(
        AndroidSingleVariantLibrary(
            javadocJar = JavadocJar.Empty(),
            sourcesJar = SourcesJar.Sources(),
            variant = "release",
        )
    )
    coordinates("com.erfangholami.androidsolidservices", "shared", "0.6.0")

    pom {
        name.set("Android Solid Services - Shared")
        description.set("A set of classes needed for SolidAndroidServices and it's dependencies.")
        inceptionYear.set("2026")
        url.set("https://github.com/erfangholami/Android-Solid-Services/")
        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/license/mit")
                distribution.set("https://opensource.org/license/mit")
            }
        }
        developers {
            developer {
                id.set("erfangholami")
                name.set("Erfan Gholami")
                url.set("https://github.com/erfangholami/")
            }
        }
        scm {
            url.set("https://github.com/erfangholami/Android-Solid-Services/")
            connection.set("scm:git:git://github.com/erfangholami/Android-Solid-Services.git")
            developerConnection.set("scm:git:ssh://git@github.com/erfangholami/Android-Solid-Services.git")
        }
    }

    publishToMavenCentral()
    signAllPublications()
}

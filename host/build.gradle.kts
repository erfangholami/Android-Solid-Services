import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.SourcesJar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.dokka)
    alias(libs.plugins.vanniktech.maven.publish)
    `maven-publish`
}

android {
    namespace = "com.erfangholami.androidsolidservices.host"
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

    // The host is built on the direct-access library: it holds the sessions and talks to the pod.
    // Both are `api` because a host wires the managers into the binders itself.
    api(project(":api"))
    api(project(":Shared"))

    implementation(libs.jetbrains.kotlinx.coroutins.core)
    implementation(libs.jetbrains.kotlinx.coroutins.android)
    implementation(libs.jetbrains.kotlinx.serialization.json)

    // The grant store persists as JSON under one Preferences key in a DataStore the host supplies.
    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.junit)
    testImplementation(libs.jetbrains.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    // Brings androidx.test:core, so Robolectric tests can reach ApplicationProvider.
    testImplementation(libs.androidx.test.ext.junit)
    // The binder tests stand in for every library manager with a recording proxy that answers
    // each verb from its declared return type, which only Kotlin reflection can read.
    testImplementation(libs.jetbrains.kotlin.reflect)
}

mavenPublishing {
    configure(
        AndroidSingleVariantLibrary(
            javadocJar = JavadocJar.Empty(),
            sourcesJar = SourcesJar.Sources(),
            variant = "release",
        )
    )
    coordinates("com.erfangholami.androidsolidservices", "host", rootProject.extra["assVersionName"] as String)

    pom {
        name.set("Android Solid Services - Host")
        description.set("Everything an app needs to host the Solid services other apps reach through the client library: the AIDL binders, the scoped access policy, the grant store and the consent protocol.")
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

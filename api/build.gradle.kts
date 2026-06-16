import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.SourcesJar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.vanniktech.maven.publish)
    alias(libs.plugins.jetbrains.kotlin.serialization)
    alias(libs.plugins.google.protobuf)
    `maven-publish`
}

android {
    namespace = "com.erfangholami.androidsolidservices.api"
    compileSdk = 36

    defaultConfig {
        minSdk = 26

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        buildConfigField(
            "String",
            "KEY_GENERATOR_ALIAS",
            project.findProperty("key_generator_alias") as String
        )
    }

    buildFeatures {
        buildConfig = true
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
}

kotlin {
    explicitApi()
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
    jvmToolchain(17)
}

dependencies {

    implementation(libs.androidx.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)

    implementation(libs.io.jsonwebtoken.api)
    runtimeOnly(libs.io.jsonwebtoken.impl)
    runtimeOnly(libs.io.jsonwebtoken.orgjson) {
        exclude(group = "org.json:json", module = "json") //provided by Android natively
    }

    //Local storage for saving profiles
    implementation(libs.jetbrains.kotlinx.serialization.json)
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.datastore.preferences)

    // Direct Solid server communication (okhttp) + JSON-LD codec (titanium). These used to arrive
    // transitively via Shared, but Shared no longer exposes them on its public API, so api — the
    // HTTP/RDF layer that genuinely uses them — now declares them itself.
    implementation(libs.okhttp)
    implementation(libs.titanium.json.ld.jre8)

    api(project(":Shared"))
}

mavenPublishing {
    configure(
        AndroidSingleVariantLibrary(
            javadocJar = JavadocJar.Empty(),
            sourcesJar = SourcesJar.Sources(),
            variant = "release",
        )
    )
    coordinates("com.erfangholami.androidsolidservices", "api", "0.5.1")

    pom {
        name.set("Android Solid Services - API")
        description.set("Connecting with Solid server in Android ecosystem for doing authentication, resource management and interacting with data modules.")
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
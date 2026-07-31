import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.SourcesJar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.dokka)
    alias(libs.plugins.jetbrains.kotlin.compose.compiler)
    alias(libs.plugins.vanniktech.maven.publish)
    `maven-publish`
}

android {
    namespace = "com.erfangholami.androidsolidservices.client"
    compileSdk = 37
    resourcePrefix = "ass_"

    lint {
        // AGP 9 always generates every report format; only the failure policy is settable.
        abortOnError = true
    }

    defaultConfig {
        minSdk = 26

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        // The instrumentation APK is a real application, so AppAuth's manifest placeholder has to
        // resolve. Never used at runtime — no authorization flow is started from these tests.
        manifestPlaceholders["appAuthRedirectScheme"] = "com.erfangholami.androidsolidservices.client.test"
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
        compose = true
    }

    testOptions {
        // Robolectric resolves the merged resources and manifest through this.
        unitTests.isIncludeAndroidResources = true
    }

    packaging {
        resources {
            excludes.addAll(
                arrayListOf(
                    "META-INF/LICENSE",
                    "META-INF/NOTICE",
                    "META-INF/DEPENDENCIES",
                    "META-INF/LICENSE.txt",
                    "META-INF/NOTICE.txt",
                    "META-INF/NOTICE.md",
                    "META-INF/ASL2.0"
                )
            )
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

    //Testing
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.jetbrains.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    // Supplies the ComponentActivity createComposeRule() launches into. It has to land in the
    // instrumentation APK, not the library's debug variant — a library has no app to merge into.
    androidTestImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(libs.junit)
    // Brings androidx.test:core, so Robolectric tests can reach ApplicationProvider.
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.jetbrains.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)

    implementation(libs.androidx.appcompat)
    implementation(libs.google.android.material)
    implementation(libs.jetbrains.kotlinx.coroutins.android)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

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
    coordinates("com.erfangholami.androidsolidservices", "client", rootProject.extra["assVersionName"] as String)

    pom {
        name.set("Android Solid Services - Client")
        description.set("An Android library to connect to Solid pods without authentication and based on connecting to Android Solid Services app as a single source of truth.")
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

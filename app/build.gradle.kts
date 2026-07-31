import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.parcelize)
    alias(libs.plugins.jetbrains.kotlin.compose.compiler)
    alias(libs.plugins.jetbrains.kotlin.serialization)
    alias(libs.plugins.google.hilt.android)
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
    alias(libs.plugins.firebase.perf)
}

android {
    namespace = "com.erfangholami.androidsolidservices"
    compileSdk = 37

    lint {
        // AGP 9 always generates every report format; only the failure policy is settable.
        abortOnError = true
    }

    defaultConfig {
        applicationId = "com.erfangholami.androidsolidservices"
        minSdk = 26
        targetSdk = 36
        versionCode = 10
        versionName = "0.6.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        manifestPlaceholders["appAuthRedirectScheme"] = "com.erfangholami.androidsolidservices"
    }

    // `foss` carries no Firebase or Google Play Services dependency, because F-Droid rejects
    // apps containing proprietary analytics outright. `gms` is what ships to Google Play.
    flavorDimensions += "distribution"
    productFlavors {
        create("gms") { dimension = "distribution" }
        create("foss") { dimension = "distribution" }
    }

    fun credential(
        property: String,
        environment: String,
    ): String? = project.findProperty(property) as String? ?: System.getenv(environment)

    val keystorePath = credential("keystore.path", "RELEASE_KEYSTORE_PATH")
    if (keystorePath != null) {
        signingConfigs {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = credential("keystore.password", "RELEASE_KEYSTORE_PASSWORD")
                keyAlias = credential("assKey.alias", "RELEASE_KEY_ALIAS")
                keyPassword = credential("assKey.password", "RELEASE_KEY_PASSWORD")

                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            manifestPlaceholders["crashlyticsEnabled"] = true
            manifestPlaceholders["performanceEnabled"] = true
            buildConfigField("boolean", "TELEMETRY_ENABLED", "true")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (keystorePath != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            isMinifyEnabled = false
            manifestPlaceholders["crashlyticsEnabled"] = false
            manifestPlaceholders["performanceEnabled"] = false
            buildConfigField("boolean", "TELEMETRY_ENABLED", "false")
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
        buildConfig = true
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

// The Google plugins register a task per variant and cannot be applied per flavour, so they also
// run for `foss` — where google-services.json deliberately does not exist and there is no Firebase
// SDK to read the resources or consume the mapping upload. Disabling their foss tasks keeps the
// plugins applied for `gms` without leaking a Firebase config into the F-Droid build.
tasks
    .matching { task ->
        task.name.contains("Foss") &&
            (
                task.name.endsWith("GoogleServices") ||
                    task.name.contains("Crashlytics") ||
                    task.name.contains("FirebasePerf")
            )
    }.configureEach { enabled = false }

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
    jvmToolchain(17)
}

composeCompiler {
}

dependencies {

    implementation(libs.jetbrains.kotlin.stdlib)
    implementation(libs.jetbrains.kotlin.stdlib.jdk8)
    implementation(libs.jetbrains.kotlin.reflect)
    implementation(libs.jetbrains.kotlinx.coroutins.core)
    implementation(libs.jetbrains.kotlinx.coroutins.android)
    implementation(libs.jetbrains.kotlinx.serialization.json)

    implementation(libs.androidx.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.startup.runtime)
    implementation(libs.androidx.annotation)

    implementation(libs.androidx.constraintLayout)
    implementation(libs.androidx.legacy.support)
    implementation(libs.androidx.exifInterface)

    //DI - Hilt
    implementation(libs.google.hilt.android)
    ksp(libs.google.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.fragment)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    //Compose
    implementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.ui.tooling)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.runtime.livedata)
    implementation(libs.androidx.compose.ui.google.fonts)

    //Lifecycle
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.lifecycle.runtime)

    //Local DataBase - Datasource
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.datastore.preferences)

    //Navigation
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.navigation.compose)

    //Worker Manager (work in the background)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.work.rxJava2)
    androidTestImplementation(libs.androidx.work.testing)
    implementation(libs.androidx.work.multiProcess)

    // Firebase reaches only the gms flavour; foss must stay free of it to be F-Droid-eligible.
    "gmsImplementation"(platform(libs.firebase.bom))
    "gmsImplementation"(libs.firebase.crashlytics)
    "gmsImplementation"(libs.firebase.performance)

    //Testing
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    testImplementation(libs.junit)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.jetbrains.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)

    implementation(project(":api"))
    implementation(project(":client"))
}

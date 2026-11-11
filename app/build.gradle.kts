import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
}

// Release signing secrets live in keystore.properties at the repo root (git-ignored).
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) keystorePropertiesFile.inputStream().use { load(it) }
}

android {
    namespace = "fr.olivier.forme"
    compileSdk = 37

    defaultConfig {
        applicationId = "fr.olivier.forme"
        minSdk = 28
        targetSdk = 37
        versionCode = 60
        versionName = "2.28.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // V2.28: two variants of the same app (same id: one replaces the other). "github" updates itself from the
    // releases repository; "play" has no update code nor REQUEST_INSTALL_PACKAGES (the Play Store forbids both).
    flavorDimensions += "distribution"
    productFlavors {
        create("github") {
            dimension = "distribution"
            buildConfigField("String", "UPDATE_REPOSITORY", "\"ollivv-hash/gymflemme-releases\"")
        }
        create("play") {
            dimension = "distribution"
        }
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
            // Single target phone (64-bit ARM): drops ~15 MB of ML Kit native code for other ABIs.
            ndk { abiFilters += "arm64-v8a" }
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets {
        // Open Exercise Illustrations (CC0): exercises.json + images, packaged as is (third_party/…/SOURCE.md).
        getByName("main") {
            assets.directories += "$rootDir/third_party/open-exercise-illustrations/assets"
        }
        // DAO and migration tests run both on device (androidTest) and on the JVM via Robolectric (test).
        getByName("test") {
            kotlin.directories += "src/sharedTest/java"
        }
        // Robolectric reads assets from the app variant: expose the Room schemas to debug only (never in release).
        getByName("debug") {
            assets.directories += "$projectDir/schemas"
        }
        getByName("androidTest") {
            kotlin.directories += "src/sharedTest/java"
            assets.directories += "$projectDir/schemas"
        }
    }

    lint {
        // Personal phone-only app: no ChromeOS (x86_64) target for the arm64-only release.
        disable += "ChromeOsAbiSupport"
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        // V2.25: the app tests start past the first-launch wizard (OnboardingSmokeTest turns it back on).
        unitTests.all {
            it.systemProperty("gymflemme.skipOnboarding", "true")
            // V2.28: no request to GitHub from the tests (UpdatePolicyTest and UpdateSmokeTest cover the update).
            it.systemProperty("gymflemme.skipUpdateCheck", "true")
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.lifecycle.viewmodel.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.datastore.preferences)
    implementation(libs.androidx.work.runtime)
    implementation(libs.glance.appwidget)
    implementation(libs.health.connect.client)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.reorderable)
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.mlkit.barcode.scanning)
    // V2.27: draws the QR code of a shared template (ML Kit only reads them).
    implementation(libs.zxing.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.room.testing)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.glance.appwidget.testing)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.turbine)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.room.testing)
}

/**
 * V2.28: the "play" variant must never ask to install packages. Fails the build when its merged manifest holds
 * REQUEST_INSTALL_PACKAGES (from the app or a library); run by `check` and before every "play" APK.
 */
abstract class CheckNoInstallPermission : DefaultTask() {
    @get:InputFile
    abstract val manifest: RegularFileProperty

    @TaskAction
    fun verify() {
        val text = manifest.get().asFile.readText()
        if ("android.permission.REQUEST_INSTALL_PACKAGES" in text) {
            throw GradleException("The play variant must not hold REQUEST_INSTALL_PACKAGES (${manifest.get().asFile}).")
        }
    }
}

androidComponents {
    onVariants(selector().withFlavor("distribution" to "play")) { variant ->
        val name = variant.name.replaceFirstChar { it.uppercase() }
        val guard = tasks.register<CheckNoInstallPermission>("check${name}NoInstallPermission") {
            manifest.set(variant.artifacts.get(com.android.build.api.artifact.SingleArtifact.MERGED_MANIFEST))
        }
        tasks.matching { it.name == "assemble$name" || it.name == "check" }.configureEach { dependsOn(guard) }
    }
}


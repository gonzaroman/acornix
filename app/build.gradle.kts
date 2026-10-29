import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.gonzaroman.acornix"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.gonzaroman.acornix"
        minSdk = 26
        targetSdk = 35
        versionCode = 45
        versionName = "2.4.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // The community gallery (gonzaroman/acornix-apps). Override with -PacornixGalleryUrl=… to test a copy.
        val galleryUrl = (project.findProperty("acornixGalleryUrl") as String?)
            ?: "https://raw.githubusercontent.com/gonzaroman/acornix-apps/main"
        buildConfigField("String", "GALLERY_URL", "\"$galleryUrl\"")
        // Public client id of the "Acornix Gallery" GitHub App, used to sign in and publish apps.
        val githubClientId = (project.findProperty("acornixGithubClientId") as String?) ?: "Iv23li4ftp0U2LGF6ovd"
        buildConfigField("String", "GITHUB_CLIENT_ID", "\"$githubClientId\"")
    }

    // Release signing comes from a keystore kept OUTSIDE the repository. Override the location with
    // -PacornixSigning=/path/keystore.properties. Without it, release builds are simply unsigned.
    val signingProps = Properties().apply {
        val path = (project.findProperty("acornixSigning") as String?)
            ?: "${System.getProperty("user.home")}/Development/acornix-signing/keystore.properties"
        val file = File(path)
        if (file.exists()) file.inputStream().use { load(it) }
    }
    signingConfigs {
        if (signingProps.getProperty("storeFile") != null) {
            create("release") {
                storeFile = File(signingProps.getProperty("storeFile"))
                storePassword = signingProps.getProperty("storePassword")
                keyAlias = signingProps.getProperty("keyAlias")
                keyPassword = signingProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*",
                "META-INF/INDEX.LIST", "META-INF/*.kotlin_module", "META-INF/versions/9/previous-compilation-data.bin",
            )
        }
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
    androidResources {
        // Sample app folders contain spaces; keep every asset.
        ignoreAssetsPattern = "!.svn:!.git:!.ds_store:!*.scc:!CVS:!thumbs.db:!picasa.ini:!*~"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.webkit)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.anthropic.java)
    implementation(libs.coil.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}

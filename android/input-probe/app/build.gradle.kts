plugins {
    id("com.android.application")
}

val sourceRevision = providers.environmentVariable("GITHUB_SHA").orElse("local-uncommitted").get()

android {
    namespace = "io.github.lightchainr.inkscapeandroid.inputprobe"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.lightchainr.inkscapeandroid.inputprobe"
        minSdk = 31
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0"
        buildConfigField("String", "GIT_SHA", "\"$sourceRevision\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}

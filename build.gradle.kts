plugins {
    id("com.android.application") version "9.3.0"
    id("com.android.library") version "9.3.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.0"
}

android {
    namespace = "com.hellovoid.liquiddock"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.hellovoid.liquiddock"
        minSdk = 33
        targetSdk = 37
        versionCode = 31
        versionName = "2.6.4"
    }

    signingConfigs {
        create("hellovoidDebug") {
            // Public, fixed debug key so CI/device-test APKs remain mutually upgradeable.
            // Release signing is intentionally external and never happens in this repository.
            storeFile = rootProject.file("signing/hellovoid-debug.keystore")
            storePassword = "hellovoid-debug"
            keyAlias = "hellovoid-debug"
            keyPassword = "hellovoid-debug"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("hellovoidDebug")
            // CI/device-test APKs go through the same R8 code + resource optimization
            // path as release, so shrinker regressions are caught before publishing.
            optimization {
                enable = true
            }
        }
        release {
            // Keep release output unsigned. The isolated liquiddock-keys workflow
            // performs zipalign + apksigner on a separate runner.
            optimization {
                enable = true
            }
        }
    }

    buildFeatures { compose = true }

    packaging {
        resources {
            merges += "META-INF/xposed/*"
        }
    }

    lint {
        disable += listOf("BlockedPrivateApi", "SoonBlockedPrivateApi")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":prismal"))

    // Experimental API 102: framework provides the hook API; companion service remains app-side.
    compileOnly("io.github.libxposed:api:102.0.0")
    implementation("io.github.libxposed:service:102.0.0")

    implementation("androidx.preference:preference:1.2.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("top.yukonga.miuix.kmp:miuix-ui-android:0.9.4")
    implementation("top.yukonga.miuix.kmp:miuix-preference-android:0.9.4")
    implementation("top.yukonga.miuix.kmp:miuix-icons-android:0.9.4")
    implementation("top.yukonga.miuix.kmp:miuix-blur-android:0.9.4")
    implementation("com.github.styropyr0:PrismalAGSL:v1.0.4")
    // Unit-test the API 102 HookHandle lifecycle with fake interface implementations.
    testImplementation("io.github.libxposed:api:102.0.0")
    testImplementation("junit:junit:4.13.2")
}

base {
    archivesName.set("LiquidDock")
}

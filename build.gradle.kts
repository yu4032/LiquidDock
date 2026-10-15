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
        versionCode = 34
        versionName = "2.7.2"
        // Pull-request Actions often run the workflow from the base branch,
        // so do not rely on a modified workflow to inject a custom env var.
        // The Actions event JSON is always provided to the build runner.
        val eventHeadSha = try {
            val eventPath = System.getenv("GITHUB_EVENT_PATH")
            val eventFile = eventPath?.let { file(it) }
            val event = eventFile?.takeIf { it.isFile }?.let {
                groovy.json.JsonSlurper().parse(it) as? Map<*, *>
            }
            val pullRequest = event?.get("pull_request") as? Map<*, *>
            val head = pullRequest?.get("head") as? Map<*, *>
            head?.get("sha") as? String
        } catch (_: Exception) {
            null
        }
        val sourceSha = System.getenv("LIQUIDDOCK_SOURCE_SHA")
            ?: eventHeadSha
            ?: System.getenv("GITHUB_SHA")
            ?: "LOCAL-UNSTAMPED"
        val runId = System.getenv("GITHUB_RUN_ID") ?: "LOCAL"
        println("LiquidDock diagnostic APK identity: head=$sourceSha ciRun=$runId")
        buildConfigField("String", "DIAG_SOURCE_SHA", "\"$sourceSha\"")
        buildConfigField("String", "DIAG_CI_RUN", "\"$runId\"")
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

    buildFeatures { compose = true; buildConfig = true }

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

    // API 101 is provided by the Xposed framework inside hooked processes.
    compileOnly("io.github.libxposed:api:101.0.1")
    // The module app uses the companion service binder to read/write Remote Preferences.
    implementation("io.github.libxposed:service:101.0.0")

    implementation("androidx.preference:preference:1.2.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("top.yukonga.miuix.kmp:miuix-ui-android:0.9.4")
    implementation("top.yukonga.miuix.kmp:miuix-preference-android:0.9.4")
    implementation("top.yukonga.miuix.kmp:miuix-icons-android:0.9.4")
    implementation("com.github.styropyr0:PrismalAGSL:v1.0.4")
    testImplementation("junit:junit:4.13.2")
}

base {
    archivesName.set("LiquidDock")
}

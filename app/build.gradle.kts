import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.konan.properties.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
    base
}

base {
    archivesName.set("sms-messenger")
}

val keystorePropertiesFile: File = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

android {
    compileSdk = project.libs.versions.app.build.compileSDKVersion.get().toInt()

    defaultConfig {
        applicationId = libs.versions.app.version.appId.get()
        minSdk = project.libs.versions.app.build.minimumSDK.get().toInt()
        targetSdk = project.libs.versions.app.build.targetSDK.get().toInt()
        versionName = project.libs.versions.app.version.versionName.get()
        versionCode = project.libs.versions.app.version.versionCode.get().toInt()
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
            }
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    buildTypes {
        debug {
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    flavorDimensions.add("variants")
    productFlavors {
        register("core")
        register("fdroid")
        register("prepaid")
        register("traccar") {
            isDefault = true
            applicationId = "org.traccar.gateway"
            versionCode = 19
            versionName = "6.0.0"
        }
    }

    sourceSets {
        getByName("main").java.srcDirs("src/main/kotlin")
    }

    compileOptions {
        val currentJavaVersionFromLibs = JavaVersion.valueOf(libs.versions.app.build.javaVersion.get().toString())
        sourceCompatibility = currentJavaVersionFromLibs
        targetCompatibility = currentJavaVersionFromLibs
    }

    tasks.withType<KotlinCompile> {
        kotlinOptions.jvmTarget = project.libs.versions.app.build.kotlinJVMTarget.get()
    }

    namespace = libs.versions.app.version.appId.get()

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation(libs.simple.mobile.tools.commons)
    implementation(libs.eventbus)
    implementation(libs.indicator.fast.scroll)
    implementation(libs.android.smsmms)
    implementation(libs.shortcut.badger)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.ez.vcard)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.bundles.room)
    ksp(libs.androidx.room.compiler)

    "traccarImplementation"(libs.javax.servlet)
    "traccarImplementation"(libs.jetty.server) {
        exclude(group = "org.eclipse.jetty.orbit", module = "javax.servlet")
    }
    "traccarImplementation"(platform(libs.firebase.bom))
    "traccarImplementation"(libs.firebase.analytics)
    "traccarImplementation"(libs.firebase.crashlytics)
    "traccarImplementation"(libs.firebase.messaging)
}

tasks.register<Copy>("copyFirebaseConfig") {
    from("../../environment/firebase")
    into(".")
    include("traccar-sms-gateway.json")
    rename("traccar-sms-gateway.json", "google-services.json")
}

afterEvaluate {
    tasks.matching { it.name.contains("Traccar") }.configureEach {
        dependsOn("copyFirebaseConfig")
    }
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "cz.tejkl.klid"
    compileSdk = 35

    defaultConfig {
        applicationId = "cz.tejkl.klid"
        minSdk = 29
        targetSdk = 35
        versionCode = 7
        versionName = "0.6.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // Podepsáno debug klíčem, aby šlo APK rovnou nainstalovat.
            signingConfig = signingConfigs.getByName("debug")
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
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            // Snímky se vždy zapíší do app/build/outputs/roborazzi.
            it.systemProperty("roborazzi.test.record", "true")
            // Robolectric stahuje Android za běhu; jde přesměrovat vlastností robolectricRepo v ~/.gradle/gradle.properties.
            providers.gradleProperty("robolectricRepo").orNull?.let { repo ->
                it.systemProperty("robolectric.dependency.repo.url", repo)
            }
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.04.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    // Snímky obrazovek vykreslené na JVM (./gradlew testDebugUnitTest), pro kontrolu vzhledu bez telefonu.
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.45.1")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.45.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

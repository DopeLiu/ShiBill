import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.shibill.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.shibill.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        // 测试版 0.1.0：正式发布前保持 0.x；检查更新（GithubReleaseChecker）按点分数字比较，
        // 如需 alpha/beta 后缀需先扩展其比较逻辑。每次分发新测试包递增 versionCode。
        versionName = "0.1.0"
    }

    signingConfigs {
        if (rootProject.file("keystore.properties").exists()) {
            create("release") {
                val props = Properties()
                rootProject.file("keystore.properties").inputStream().use { props.load(it) }
                storeFile = rootProject.file(props.getProperty("storeFile") ?: "release.keystore")
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // ML Kit bundled 模型自带 4 个 ABI 的 native 库（合计约 41MB，含 x86 模拟器架构）；
            // 发布版仅保留 ARM 双架构，将体积增幅控制在方案 §10 M3 要求的 <40MB 以内。
            ndk {
                abiFilters += listOf("arm64-v8a", "armeabi-v7a")
            }
            if (rootProject.file("keystore.properties").exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
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
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.mlkit.text.recognition.chinese)
    implementation(libs.vico.compose.m3)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    debugImplementation(libs.androidx.compose.ui.tooling)
}

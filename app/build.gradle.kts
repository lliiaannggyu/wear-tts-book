plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.tengwear.ttsbookm3e"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tengwear.ttsbookm3e"
        // 25 是依赖图的硬性地板：androidx.wear.compose 的 compose-material3 /
        // compose-foundation / compose-material-core 三者的 AAR 均声明 minSdkVersion=25。
        // 再低就必须放弃 M3E 组件库，故 25 为保留现有功能前提下的最低值。
        // 代码中 API 26+ 的调用（startForegroundService 等）已用 Build.VERSION.SDK_INT 门控。
        minSdk = 25
        targetSdk = 35
        versionCode = 2
        versionName = "1.0"
    }

    buildTypes {
        release {
            // ★ 开启 R8 代码压缩 + 混淆 + 优化
            isMinifyEnabled = true
            // ★ 开启资源压缩（移除未引用的资源）
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // ==================== Wear OS Compose Material 3 (M3E) ====================
    // M3E 组件库：提供 MaterialTheme / ColorScheme / Button / Card / Picker / SwitchButton 等纯正组件
    // 注意：1.5.0 要求 minCompileSdk = 35 且 AGP >= 8.6.0
    implementation("androidx.wear.compose:compose-material3:1.5.0")
    implementation("androidx.wear.compose:compose-foundation:1.5.0")

    // Compose BOM — 用 BOM 统一管理 compose 基础库版本
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.code.gson:gson:2.10.1")

    // 协程与 Lifecycle
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
}
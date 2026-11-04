plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.sofatype.ime"
    compileSdk = 34 // 对齐 nix SDK 内 platform android-34

    defaultConfig {
        applicationId = "com.sofatype.ime"
        // PRD 第 12 章决策 13：Android TV 6.0+；验收机小米电视4（MiTV4-ANSM0）为 Android 6.0.1
        minSdk = 23
        targetSdk = 34
        // PRD 第 12 章决策 19：首版版本基线 0.1.0(1)，随发布批次递增
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            // 首版不做混淆与裁剪，缩小构建变量面；后续版本需要时再评估
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("app.cash.paparazzi")
}

base {
    archivesName = "sofatype"
}

android {
    namespace = "com.sofatype.ime"
    compileSdk = 34 // 对齐 nix SDK 内 platform android-34

    defaultConfig {
        applicationId = "com.sofatype.ime"
        // PRD 第 12 章决策 13：Android TV 6.0+；验收机小米电视4（MiTV4-ANSM0）为 Android 6.0.1
        minSdk = 23
        targetSdk = 34
        // PRD 第 12 章决策 19：版本随发布批次递增，首版基线 0.1.0(1)，当前 0.2.0(2)（T89 起）
        versionCode = 2
        versionName = "0.2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // 首版不做混淆与裁剪，缩小构建变量面；后续版本需要时再评估
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        // T2（PRD 决策 21）：测试输入区以 BuildConfig.DEBUG 门控，需生成 BuildConfig 类
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    sourceSets {
        // T9（PRD 决策 28）：拼音词库 SPD1 转换产物入包 —— pinyin 模块的
        // assets/dict/pinyin_dict.bin 以 APK assets 形式内置（原始源文件不入库）。
        // T85（方向 C）：同目录高频子集产物 pinyin_dict_highfreq.bin 一并入包。
        getByName("main") {
            assets.srcDir(rootDir.resolve("pinyin/assets"))
        }
    }
}

// T10 接线补依赖边：app 依赖 :pinyin 后，:pinyin 任务与 app 任务进入同一构建图，
// merge{Debug,Release}Assets 消费 pinyin/assets（convertDict 的输出目录）必须显式声明依赖，
// 否则 Gradle 隐式依赖校验直接失败（T32：release 变体此前漏配，assembleRelease 报错）；
// 先过 sha256 门禁与转换，再合并 assets。
// 变体任务由 AGP 后注册，用惰性匹配挂依赖；debug / release 两变体共用 assets 源目录，边都要挂。
tasks.matching { it.name == "mergeDebugAssets" || it.name == "mergeReleaseAssets" }.configureEach {
    dependsOn(":pinyin:buildDict")
}

dependencies {
    // T3：占位键盘纯逻辑单元测试（仅 test 作用域，不进产品依赖，AGENTS.md 禁止事项 1）
    testImplementation("junit:junit:4.13.2")
    // T10：拼音引擎接线（FR-A8）——:pinyin 为本仓库自有纯 Kotlin JVM 模块（决策 28，无第三方依赖）
    implementation(project(":pinyin"))
    // T14（FR-B4）：二维码生成用 ZXing core（工具链拍板 2026-11-03「二维码 ZXing core」；
    // 仅 core 纯 Java 库，无 ZXing Android 嵌入件等第三方 SDK）
    implementation("com.google.zxing:core:3.5.3")
    // T71 · 决策 60：日志组件 Timber（户主明确指令，「纯 Kotlin 无第三方 SDK」约束的指令例外，
    // 随 ZXing 先例入册；issue T71 如实记录）——双树 = logcat 树（行为同现状）+ 文件树（开关控制）。
    implementation("com.jakewharton.timber:timber:5.0.1")
    androidTestImplementation("androidx.test:core:1.5.0")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test:rules:1.5.0")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
}

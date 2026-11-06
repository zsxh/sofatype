// T9 · 拼音引擎独立模块（纯 Kotlin JVM，无 Android 依赖；词库口径 PRD 决策 28，源文件入库修订见决策 29）
// 职责：SPD1 词库格式读写、词库源文件校验与构建期转换（零网络）、拼音切分、词组级候选与排序。
plugins {
    id("org.jetbrains.kotlin.jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        // 与 app 模块 jvmTarget 保持一致（nix flake 内 JDK 17）
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// pinyin/assets/ 下除源文件外的内容一并作为 JVM resources：
// 单测通过 classpath 读取词库转换产物（/dict/pinyin_dict.bin），
// 与 app 模块经由 assets 源目录读到的同一份产物保持一致。
sourceSets {
    main {
        resources.srcDir("assets")
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

tasks.withType<Test>().configureEach {
    useJUnit()
    // 显式钉住测试工作目录 = 模块目录（Gradle 默认行为一致，但不依赖其隐式约定）：
    // VerifyDictTest 以相对路径 dictsrc/… 访问入库源文件
    workingDir = projectDir
    testLogging {
        events("failed", "skipped")
        showStandardStreams = false
    }
}

// 词库源文件（AOSP 谷歌拼音词库，UTF-16 文本，随仓库入库，sha256 锁定，PRD 决策 29）
val dictSourceFile = layout.projectDirectory.file("dictsrc/rawdict_utf16_65105_freq.txt")
// 转换产物（自有精简格式 SPD1，入包：app assets 源目录指向 pinyin/assets/；构建产物不入库）
val dictOutputFile = layout.projectDirectory.file("assets/dict/pinyin_dict.bin")

// 构建期门禁：校验仓库内源文件存在且 sha256 与锁定值一致（PRD 决策 29：构建零网络，无下载逻辑）。
// 无输出声明 → 每次构建都执行校验（3.5 MB 哈希约几十毫秒），源文件被改后立即暴露。
tasks.register<JavaExec>("verifyDict") {
    group = "sofatype"
    description = "校验词库源文件 sha256 与入库基线一致（决策 29）"
    // 只依赖编译输出与外部类路径（不含 resources）：processResources 依赖 buildDict，
    // 此处若用 runtimeClasspath 会成循环依赖
    classpath = sourceSets["main"].compileClasspath + files(sourceSets["main"].output.classesDirs)
    mainClass.set("com.sofatype.pinyin.tools.VerifyDictKt")
    args(dictSourceFile.asFile.absolutePath)
}

// 构建期转换：UTF-16 源文本 → SPD1 精简格式产物。
// 输入输出声明：源文件与转换器版本未变且产物已存在时 up-to-date 跳过。
tasks.register<JavaExec>("convertDict") {
    group = "sofatype"
    description = "词库源文件构建期转换为 SPD1 精简格式（决策 28/29）"
    classpath = sourceSets["main"].compileClasspath + files(sourceSets["main"].output.classesDirs)
    mainClass.set("com.sofatype.pinyin.tools.ConvertDictKt")
    args(dictSourceFile.asFile.absolutePath, dictOutputFile.asFile.absolutePath)
    dependsOn("verifyDict") // 先过 sha256 门禁，再转换
    inputs.file(dictSourceFile)
    outputs.file(dictOutputFile)
}

// 一站式任务：校验源文件 + 转换产物
tasks.register("buildDict") {
    group = "sofatype"
    description = "校验词库源文件并转换为 SPD1 产物（零网络）"
    dependsOn("verifyDict", "convertDict")
}

// 构建期转换入包口径（决策 28/29）：测试资源与 APK assets 复制前先保证产物就绪，
// 避免「复制旧产物 / 转换新产物」在同轮构建中顺序不定。
tasks.named("processResources") {
    dependsOn("buildDict")
}

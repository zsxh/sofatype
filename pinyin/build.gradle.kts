// T9 · 拼音引擎独立模块（纯 Kotlin JVM，无 Android 依赖；词库口径 PRD 决策 28，源文件入库修订见决策 29）
// 职责：SPD1 词库格式读写、词库源文件校验与构建期转换（零网络）、拼音切分、词组级候选与排序。
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("me.champeau.jmh")
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
    jmh("org.openjdk.jmh:jmh-core:1.37")
}

jmh {
    resultFormat.set("JSON")
    resultsFile.set(layout.buildDirectory.file("reports/jmh/results.json"))
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
// 高频子集产物（T85 · 方向 C：词条按内建频次降序取前 4000，音节表全量保留、词表紧缩重映射；
// 与全量产物同源同时生成、同 SPD1 格式；构建产物不入库）
val dictHighFreqOutputFile = layout.projectDirectory.file("assets/dict/pinyin_dict_highfreq.bin")
val dictHighFreqEntryLimit = 4000

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

// 构建期转换：UTF-16 源文本 → SPD1 精简格式产物（全量 + 高频子集，同源一次生成）。
// 输入输出声明：源文件与转换器版本未变且产物已存在时 up-to-date 跳过。
tasks.register<JavaExec>("convertDict") {
    group = "sofatype"
    description = "词库源文件构建期转换为 SPD1 精简格式（决策 28/29）+ 高频子集产物（T85 方向 C）"
    classpath = sourceSets["main"].compileClasspath + files(sourceSets["main"].output.classesDirs)
    mainClass.set("com.sofatype.pinyin.tools.ConvertDictKt")
    args(
        dictSourceFile.asFile.absolutePath,
        dictOutputFile.asFile.absolutePath,
        dictHighFreqOutputFile.asFile.absolutePath,
        dictHighFreqEntryLimit.toString(),
    )
    dependsOn("verifyDict") // 先过 sha256 门禁，再转换
    inputs.file(dictSourceFile)
    outputs.file(dictOutputFile)
    outputs.file(dictHighFreqOutputFile)
}

// 一站式任务：校验源文件 + 转换产物
tasks.register("buildDict") {
    group = "sofatype"
    description = "校验词库源文件并转换为 SPD1 产物（零网络）"
    dependsOn("verifyDict", "convertDict")
}

// T96 · 热词内置离线化：热词源入库 + sha256 门禁 + 构建期转 SPD1（零网络，逐词库开关）；
// T99 修订：baidu-realtime（百度实时热搜榜）停止并入、退出默认源（源文件与抓取脚本保留供复评）；
// 热词频次构建期按词长分档重写为绝对频次（HotDictBuild.tierFreq：2~3 字 2500 / ≥4 字 300），运行时原样并入不再 ×10。
// 热词源文件（入库 pinyin/dictsrc/hotwords/，sha256 锁定见 sources.sha256）：
// id -> (文件, 原料类型)；scel = 搜狗细胞词库原件（自带拼音），hot = 注音源文本行（词 频次 0 拼音…）。
val hotDictSources = linkedMapOf(
    "sogou" to Triple("sogou-liuxingxinci.scel", "scel", "搜狗网络流行新词累积库"),
    "baidu-boiling" to Triple("baidu-boiling-years.txt", "hot", "百度沸点/搜索风云榜历年榜（人工整理）"),
    "nlc-netlang" to Triple("nlc-netlang-years.txt", "hot", "国家语言资源监测中心年度网络用语"),
    "yaowenjiaozi" to Triple("yaowenjiaozi-years.txt", "hot", "咬文嚼字年度十大流行语"),
)
val hotDictSourceDir = layout.projectDirectory.dir("dictsrc/hotwords")
val hotDictLockFile = hotDictSourceDir.file("sources.sha256")
val hotDictOutputFile = layout.projectDirectory.file("assets/dict/hotwords.bin")

// 构建开关（户主 2026-11-22 拍板：默认全内置）：-PsofatypeHotDicts=sogou,baidu-boiling 选择子集；
// 未设置 = 全部热词源；设为空值（-PsofatypeHotDicts=）= 全不内置（产物为空 SPD1，运行时回退纯主词库）；
// 未知 id 构建失败。
val hotDictSelected: List<String> = run {
    val raw = findProperty("sofatypeHotDicts") as String?
    if (raw == null) {
        hotDictSources.keys.toList()
    } else {
        val picked = raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val unknown = picked.filter { it !in hotDictSources }
        require(unknown.isEmpty()) { "未知热词源 id：$unknown（可选：${hotDictSources.keys}）" }
        hotDictSources.keys.filter { it in picked.toSet() }
    }
}

tasks.register<JavaExec>("verifyHotDict") {
    group = "sofatype"
    description = "校验热词源文件 sha256 与入库锁定值一致（sources.sha256）"
    classpath = sourceSets["main"].compileClasspath + files(sourceSets["main"].output.classesDirs)
    mainClass.set("com.sofatype.pinyin.tools.VerifyHotDictKt")
    args(hotDictSourceDir.asFile.absolutePath)
    inputs.file(hotDictLockFile)
    inputs.files(hotDictSources.values.map { hotDictSourceDir.file(it.first) })
}

tasks.register<JavaExec>("buildHotDict") {
    group = "sofatype"
    description = "热词源合并转换为 SPD1 内置产物（零网络；-PsofatypeHotDicts 逐词库开关）"
    classpath = sourceSets["main"].compileClasspath + files(sourceSets["main"].output.classesDirs)
    mainClass.set("com.sofatype.pinyin.tools.HotDictBuildKt")
    dependsOn("verifyDict", "verifyHotDict")
    doFirst {
        val selected = hotDictSelected.ifEmpty {
            println("[hotDictBuild] 热词源全不内置（sofatypeHotDicts 为空），产物为空 SPD1")
            emptyList()
        }
        val materials = selected.map { id ->
            val (file, kind, label) = hotDictSources.getValue(id)
            println("[hotDictBuild] 热词源 ${id}（${label}）：${kind}:${hotDictSourceDir.file(file).asFile.absolutePath}")
            "${kind}:${hotDictSourceDir.file(file).asFile.absolutePath}"
        }
        args(
            hotDictOutputFile.asFile.absolutePath,
            dictSourceFile.asFile.absolutePath,
        )
        if (materials.isNotEmpty()) args(materials)
    }
    inputs.file(dictSourceFile)
    inputs.files(hotDictSelected.map { hotDictSourceDir.file(hotDictSources.getValue(it).first) })
    outputs.file(hotDictOutputFile)
}

// T96 热词构建任务见上方 buildHotDict 段（词包样例产出与 manifest 分发链路随 T94/T95 废止移除）

// 构建期转换入包口径（决策 28/29）：测试资源与 APK assets 复制前先保证产物就绪，
// 避免「复制旧产物 / 转换新产物」在同轮构建中顺序不定。
tasks.named("processResources") {
    dependsOn("buildDict", "buildHotDict")
}

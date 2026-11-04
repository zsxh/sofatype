// sofatype 输入法 · 工程骨架（T1）
// 仓库口径：Gradle 工程直接位于仓库根（PRD 第 12 章决策 14）。
pluginManagement {
    repositories {
        google() // AGP 与 Android 构建工具
        mavenCentral() // Kotlin 插件与标准库
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "sofatype"
include(":app")

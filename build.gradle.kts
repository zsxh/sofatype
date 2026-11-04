// 顶层构建配置：插件版本在此固定，子模块不再重复声明版本。
// 构建口径（HANDOFF 拍板）：日常构建只走 ./gradlew；wrapper 版本见 gradle/wrapper/gradle-wrapper.properties。
plugins {
    id("com.android.application") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
}

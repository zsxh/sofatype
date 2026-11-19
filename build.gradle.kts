// 顶层构建配置：插件版本在此固定，子模块不再重复声明版本。
// 构建口径（HANDOFF 拍板）：日常构建只走 ./gradlew；wrapper 版本见 gradle/wrapper/gradle-wrapper.properties。
import org.gradle.api.attributes.java.TargetJvmEnvironment

plugins {
    id("com.android.application") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    // T9：拼音引擎独立模块为纯 Kotlin JVM 模块，与 android 模块共用同一 Kotlin 版本
    id("org.jetbrains.kotlin.jvm") version "1.9.24" apply false
    id("app.cash.paparazzi") version "1.3.3" apply false
}

subprojects {
    plugins.withId("app.cash.paparazzi") {
        afterEvaluate {
            dependencies.constraints {
                add("testImplementation", "com.google.guava:guava") {
                    attributes {
                        attribute(
                            TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE,
                            objects.named(TargetJvmEnvironment::class.java, TargetJvmEnvironment.STANDARD_JVM),
                        )
                    }
                    because("LayoutLib and sdk-common depend on Guava's -jre published variant. (cashapp/paparazzi#906)")
                }
            }
        }
    }
}

package com.sofatype.ime.dicts

import org.junit.Assert.assertFalse
import java.io.File
import org.junit.Test

class DictPackPrivacyGateTest {

    @Test
    fun `词包链路源码零设备标识与零输入内容面引用`() {
        val sources = listOf(
            "src/main/java/com/sofatype/ime/dicts/DictPackEndpoint.kt",
            "src/main/java/com/sofatype/ime/dicts/DictPackCatalog.kt",
            "src/main/java/com/sofatype/ime/dicts/DictPackStore.kt",
            "src/main/java/com/sofatype/ime/dicts/DictPackManifestParser.kt",
            "src/main/java/com/sofatype/ime/dicts/DictPackEngineSource.kt",
            "src/main/java/com/sofatype/ime/dicts/DictPackRefresher.kt",
            "src/main/java/com/sofatype/ime/DictPackSettingsActivity.kt",
        )
        val banned = listOf(
            "ANDROID_ID",
            "getDeviceId",
            "Settings.Secure",
            "IMEI",
            "advertisingId",
            "composer",
            "candidate",
            "commitText",
            "editText",
            "remoteText",
            "deviceName",
            "test_input",
            "X-Device",
            "uuid",
        )
        for (relative in sources) {
            val source = File(relative).readText()
            for (identifier in banned) {
                assertFalse(
                    "$relative 不得引用设备标识 / 输入内容面标识 $identifier（隐私红线）",
                    source.contains(identifier, ignoreCase = true),
                )
            }
        }
    }

    @Test
    fun `词包下载只复用UpdateHttp通道_匿名GET不带自定义头`() {
        val source = File("src/main/java/com/sofatype/ime/dicts/DictPackCatalog.kt").readText() +
            File("src/main/java/com/sofatype/ime/DictPackSettingsActivity.kt").readText()
        assertFalse("词包链路不得自建网络请求（统一复用 UpdateHttp）", source.contains("openConnection"))
        assertFalse("词包链路不得添加请求头", source.contains("setRequestProperty"))
    }
}

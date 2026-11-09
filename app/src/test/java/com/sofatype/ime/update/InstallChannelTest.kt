// T47 · 决策 36 渠道门控单元测试：锁定「会话分支保留为 API 24+」边界——
// API 24 起走 PackageInstaller 会话（验收④回归口径），API 23 及以下走 UI 渠道
// （系统安装器 install Intent，会话直装口径在 Android 6 基线废止）。
package com.sofatype.ime.update

import org.junit.Assert.assertEquals
import org.junit.Test

class InstallChannelTest {

    @Test
    fun boundaryIsExactlyAtApi24() {
        assertEquals(InstallChannel.UI_INTENT, InstallChannels.channelFor(23))
        assertEquals(InstallChannel.SESSION, InstallChannels.channelFor(24))
    }

    @Test
    fun lowerApisTakeUiChannel() {
        assertEquals(InstallChannel.UI_INTENT, InstallChannels.channelFor(21))
        assertEquals(InstallChannel.UI_INTENT, InstallChannels.channelFor(22))
    }

    @Test
    fun higherApisKeepSessionChannel() {
        assertEquals(InstallChannel.SESSION, InstallChannels.channelFor(25))
        assertEquals(InstallChannel.SESSION, InstallChannels.channelFor(33))
        // T47 验收④：模拟器 API 34 会话分支回归不破坏
        assertEquals(InstallChannel.SESSION, InstallChannels.channelFor(34))
    }

    @Test
    fun sessionMinSdkConstantIs24() {
        // 决策 36「保留为 API 24+ 分支」字面锁定：边界值漂移即红
        assertEquals(24, InstallChannels.SESSION_MIN_SDK)
    }
}

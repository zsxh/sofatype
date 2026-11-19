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

        assertEquals(InstallChannel.SESSION, InstallChannels.channelFor(34))
    }

    @Test
    fun sessionMinSdkConstantIs24() {

        assertEquals(24, InstallChannels.SESSION_MIN_SDK)
    }
}

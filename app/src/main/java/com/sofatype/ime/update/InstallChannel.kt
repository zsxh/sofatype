package com.sofatype.ime.update

enum class InstallChannel {

    SESSION,

    UI_INTENT,
}

object InstallChannels {

    const val SESSION_MIN_SDK = 24

    fun channelFor(sdkInt: Int): InstallChannel =
        if (sdkInt >= SESSION_MIN_SDK) InstallChannel.SESSION else InstallChannel.UI_INTENT
}

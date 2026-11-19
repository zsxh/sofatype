package com.sofatype.ime.remote

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.sofatype.ime.BuildConfig

class RemoteServerDebugReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!BuildConfig.DEBUG) return
        when (intent.action) {
            ACTION_START -> RemoteInputServerManager.start(context)
            ACTION_STOP -> {
                val stopped = RemoteInputServerManager.stop()
                if (!stopped) Log.i(TAG, "服务未在运行，无需停止")
            }

            ACTION_SIMULATE_NETWORK_CHANGE -> RemoteInputServerManager.debugNetworkChange()

            ACTION_SIMULATE_CONNECTED_IDLE_EXPIRE -> RemoteInputServerManager.debugConnectedIdleExpire()
            ACTION_STATUS -> {
                val url = RemoteInputServerManager.addressUrl()
                val pairingUrl = RemoteInputServerManager.pairingUrl()
                Log.i(
                    TAG,
                    when {
                        url == null -> "当前状态：未运行"

                        RemoteInputServerManager.hasPairedSession() ->
                            "当前状态：运行中 $url；已配对会话在线；配对地址（滚动新码）：$pairingUrl"
                        else -> "当前状态：运行中 $url；配对地址：$pairingUrl（等待扫码）"
                    },
                )
            }
            else -> Unit
        }
    }

    companion object {
        const val TAG = "RemoteInputServer"
        const val ACTION_START = "com.sofatype.ime.debug.action.START_REMOTE_SERVER"
        const val ACTION_STOP = "com.sofatype.ime.debug.action.STOP_REMOTE_SERVER"
        const val ACTION_STATUS = "com.sofatype.ime.debug.action.STATUS_REMOTE_SERVER"

        const val ACTION_SIMULATE_NETWORK_CHANGE = "com.sofatype.ime.debug.action.SIMULATE_NETWORK_CHANGE"

        const val ACTION_SIMULATE_CONNECTED_IDLE_EXPIRE = "com.sofatype.ime.debug.action.SIMULATE_CONNECTED_IDLE_EXPIRE"
    }
}

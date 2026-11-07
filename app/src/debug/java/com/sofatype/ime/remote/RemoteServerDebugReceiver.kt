// T13 · 远程输入服务 debug 走查入口（决策 21 口径：仅 debug 构建）。
// 该接收器只注册在 app/src/debug/AndroidManifest.xml，release 合并不包含此文件；
// onReceive 再检查 BuildConfig.DEBUG 兜底。
// 走查：am broadcast 启动/停止/查询状态，结果经 logcat（TAG=RemoteInputServer）确认。
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
            ACTION_STATUS -> {
                val url = RemoteInputServerManager.addressUrl()
                Log.i(TAG, if (url != null) "当前状态：运行中 $url" else "当前状态：未运行")
            }
            else -> Unit
        }
    }

    companion object {
        const val TAG = "RemoteInputServer"
        const val ACTION_START = "com.sofatype.ime.debug.action.START_REMOTE_SERVER"
        const val ACTION_STOP = "com.sofatype.ime.debug.action.STOP_REMOTE_SERVER"
        const val ACTION_STATUS = "com.sofatype.ime.debug.action.STATUS_REMOTE_SERVER"
    }
}

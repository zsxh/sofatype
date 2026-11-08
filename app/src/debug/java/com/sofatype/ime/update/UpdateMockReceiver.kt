// T21 · 检查更新 debug 走查入口（决策 21 同口径：仅 debug 构建，release 无此组件）。
// 用法：am broadcast 写进程内存级接口地址覆写位（决策 17 mock 联调通道，仅内存不持久化），
// 供模拟器指向本机 mock HTTP 源（GitHub Releases 字段格式的 JSON + APK 资产）。
// 例：adb shell am broadcast -n com.sofatype.ime/.update.UpdateMockReceiver
//     -a com.sofatype.ime.debug.action.SET_UPDATE_ENDPOINT -e endpoint http://10.0.2.2:8000/releases.json
package com.sofatype.ime.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.sofatype.ime.BuildConfig

class UpdateMockReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!BuildConfig.DEBUG) return
        when (intent.action) {
            ACTION_SET_ENDPOINT -> {
                val endpoint = intent.getStringExtra(EXTRA_ENDPOINT)
                UpdateEndpoint.setDebugOverride(endpoint)
                Log.i(TAG, "更新源接口地址覆写：${UpdateEndpoint.endpoint(context)}")
            }
            else -> Unit
        }
    }

    companion object {
        const val TAG = "UpdateCheck"
        const val ACTION_SET_ENDPOINT = "com.sofatype.ime.debug.action.SET_UPDATE_ENDPOINT"
        const val EXTRA_ENDPOINT = "endpoint"
    }
}

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
            // T18 · 决策 21 口径 debug 走查通道：模拟「运行中网络丢失后恢复」，走真实判定与重建路径
            //（模拟器 eth0 默认网络无法经 shell 注销，真实回调触发随 T19 真机验证）
            ACTION_SIMULATE_NETWORK_CHANGE -> RemoteInputServerManager.debugNetworkChange()
            // T70 · 决策 21 口径 debug 走查通道：模拟「已连接无活动超时」到期，走真实判定与
            // 停服清理路径（活动回拨一个完整窗口后立即到期，正式 10 分钟阈值不改动）
            ACTION_SIMULATE_CONNECTED_IDLE_EXPIRE -> RemoteInputServerManager.debugConnectedIdleExpire()
            ACTION_STATUS -> {
                val url = RemoteInputServerManager.addressUrl()
                val pairingUrl = RemoteInputServerManager.pairingUrl()
                Log.i(
                    TAG,
                    when {
                        url == null -> "当前状态：未运行"
                        // 配对地址含一次性 token，仅 debug 走查日志输出（FR-B4 地址与二维码内容一致；
                        // 决策 35 滚动签发：已配对后 token 为滚动新签发值，二维码内容同步更新）
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

        /** T18 debug 走查通道：模拟网络丢失后恢复，触发真实判定与重建路径。 */
        const val ACTION_SIMULATE_NETWORK_CHANGE = "com.sofatype.ime.debug.action.SIMULATE_NETWORK_CHANGE"

        /** T70 debug 走查通道：模拟「已连接无活动超时」到期，触发真实判定与停服清理路径。 */
        const val ACTION_SIMULATE_CONNECTED_IDLE_EXPIRE = "com.sofatype.ime.debug.action.SIMULATE_CONNECTED_IDLE_EXPIRE"
    }
}

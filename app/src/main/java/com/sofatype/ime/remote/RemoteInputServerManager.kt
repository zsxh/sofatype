// T13 · 远程输入服务启停生命周期（TASKS T13：「随远程模式启停、退出端口无残留」）。
// 远程模式 UI 属 T15/T17：届时远程模式进入（FR-B2）调 start、退出（FR-B7）调 stop。
// T13 阶段走查入口为 debug 广播接收器（app/src/debug，决策 21 同口径：release 不暴露）。
// 本层是唯一的 android.* 依赖面：地址收集（ConnectivityManager / NetworkInterface）与日志。
package com.sofatype.ime.remote

import android.content.Context
import android.net.ConnectivityManager
import android.util.Log
import com.sofatype.ime.BuildConfig
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketException

object RemoteInputServerManager {
    private const val TAG = "RemoteInputServer"

    @Volatile
    private var server: RemoteInputServer? = null

    @Volatile
    private var addressText: String? = null

    /** 当前服务是否在监听。 */
    fun isRunning(): Boolean = server?.isRunning == true

    /** 运行中的访问地址文本（如 http://10.0.2.15:32923/）；未运行返回 null。 */
    fun addressUrl(): String? {
        val addr = addressText
        val port = server?.port
        return if (addr != null && port != null) "http://$addr:$port/" else null
    }

    /**
     * 启动服务：解析默认路由 IPv4 → 随机端口绑定 → 进入监听。
     * 仅 debug 构建可用（决策 21 口径）；release 返回 Failed，不做任何动作。
     * 失败只返回结果，不抛异常（第 8 章可靠性：不得影响 IME 进程）。
     */
    fun start(context: Context): RemoteInputServer.StartResult {
        if (!BuildConfig.DEBUG) {
            return RemoteInputServer.StartResult.Failed("远程输入服务启动入口仅 debug 构建可用（决策 21）")
        }
        synchronized(this) {
            if (isRunning()) {
                return RemoteInputServer.StartResult.Failed("服务已在运行（${addressUrl()}）")
            }
            val address = resolveLanAddress(context)
            if (address == null) {
                val reason = "未找到可绑定的局域网 IPv4 接口"
                Log.w(TAG, "启动失败：$reason")
                return RemoteInputServer.StartResult.Failed(reason)
            }
            val instance = RemoteInputServer(address) // 端口随机：requestedPort 默认 0（决策 16）
            val result = instance.start()
            when (result) {
                is RemoteInputServer.StartResult.Started -> {
                    server = instance
                    addressText = address.hostAddress
                    // 验收通道：端口经此条日志获知（adb logcat -s RemoteInputServer）
                    Log.i(TAG, "远程输入服务已启动：${addressUrl()}（WS 端点同端口 /ws）")
                }
                is RemoteInputServer.StartResult.Failed ->
                    Log.w(TAG, "启动失败：${result.reason}")
            }
            return result
        }
    }

    /** 停止服务并确认无端口残留；返回停止前是否在运行。幂等。 */
    fun stop(): Boolean {
        synchronized(this) {
            val instance = server ?: return false
            server = null
            addressText = null
            instance.stop()
            Log.i(TAG, "远程输入服务已停止，监听已关闭")
            return true
        }
    }

    /**
     * 收集候选接口 → 交给纯逻辑层 [BindAddressSelector] 按决策 16 选取：
     * 优先默认路由接口（来自活动网络的 LinkProperties），回退站点本地 IPv4；
     * 回环接口不参与；找不到返回 null。
     */
    private fun resolveLanAddress(context: Context): InetAddress? {
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val defaultIfaceName = try {
            connectivity?.activeNetwork?.let { connectivity.getLinkProperties(it)?.interfaceName }
        } catch (_: SecurityException) {
            null
        }
        val candidates = try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            val list = if (interfaces != null) java.util.Collections.list(interfaces) else emptyList()
            list.mapNotNull { nic ->
                try {
                    NicCandidate(
                        name = nic.name,
                        ipv4 = BindAddressSelector.pickIpv4(
                            java.util.Collections.list(nic.inetAddresses)
                                .filterIsInstance<Inet4Address>()
                                .mapNotNull { it.hostAddress },
                        ),
                        isUp = nic.isUp,
                        isLoopback = nic.isLoopback,
                        isDefaultRoute = nic.name == defaultIfaceName,
                    )
                } catch (_: SocketException) {
                    null // 单个接口枚举异常不影响其余接口
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
        return BindAddressSelector.select(candidates)?.ipv4?.let { InetAddress.getByName(it) }
    }
}

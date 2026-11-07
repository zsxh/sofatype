// T13 · 远程输入服务启停生命周期（TASKS T13：「随远程模式启停、退出端口无残留」）。
// 远程模式 UI 属 T15/T17：届时远程模式进入（FR-B2）调 start、退出（FR-B7）调 stop。
// T13 阶段走查入口为 debug 广播接收器（app/src/debug，决策 21 同口径：release 不暴露）。
// T14 增量（FR-B4/B10）：启动时签发一次性配对 token，/ws 装配 hello/state 配对会话；
// 配对地址（含 token）随启动日志输出供走查，仅 debug 构建可达；token 与服务同生命周期，停止即清空。
// T15 增量（FR-C4/B3/B5）：GET / 下发 APK assets 内 H5 页（决策 20）；/ws 以会话工厂按连接
// 创建配对会话，input/clear 业务回调经主线程 Handler 通知 UI（onRemoteTextChanged）；
// 远程文本缓冲仅驻内存（RemoteTextBuffer，200 字码点上限兜底），停止即清空、日志不涉内容。
// 本层是唯一的 android.* 依赖面：地址收集（ConnectivityManager / NetworkInterface）、TV 名与日志。
package com.sofatype.ime.remote

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.sofatype.ime.BuildConfig
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketException

object RemoteInputServerManager {
    private const val TAG = "RemoteInputServer"

    /** H5 页 assets 路径（决策 20：打进 APK assets 由本服务下发）。 */
    private const val MAIN_PAGE_ASSET = "remote/index.html"

    @Volatile
    private var server: RemoteInputServer? = null

    @Volatile
    private var addressText: String? = null

    /** 一次性配对 token（FR-B10）：每次启动签发新值，停止即清空。 */
    private val tokens = PairingTokenStore()

    /** 本轮服务的配对地址（含 token，FR-B4「地址与二维码内容一致」）；仅服务运行期间非空。 */
    @Volatile
    private var pairingUrlText: String? = null

    /** 远程输入缓冲（T15 · FR-C4）：仅驻内存，服务停止即清空（第 8 章隐私）。 */
    private val remoteBuffer = RemoteTextBuffer(PairingSession.DEFAULT_CHAR_LIMIT)

    /** UI 回调主线程派发（WS 读线程回调不可直接碰视图）。 */
    private val mainHandler = Handler(Looper.getMainLooper())

    /** H5 页字节缓存（静态资源，非用户内容；首次请求后驻内存，避免每次请求读 assets）。 */
    @Volatile
    private var mainPageCache: ByteArray? = null

    /**
     * 配对状态变化回调（T14 走查接线）：启动成功 / 停止后通知，UI 据此装载或清空配对卡。
     * 回调在主线程执行（mainHandler 派发）；远程模式完整状态机随 T17 重构。
     */
    @Volatile
    var onPairingStateChanged: (() -> Unit)? = null

    /**
     * 远程文本变化回调（T15 · FR-B3/B5）：input 后 = 全量文本，clear / 服务停止后 = null。
     * 回调保证在主线程执行；内容仅经内存传递，任何日志不记录。
     */
    @Volatile
    var onRemoteTextChanged: ((String?) -> Unit)? = null

    /** 当前服务是否在监听。 */
    fun isRunning(): Boolean = server?.isRunning == true

    /** 运行中的访问地址文本（如 http://10.0.2.15:32923/）；未运行返回 null。 */
    fun addressUrl(): String? {
        val addr = addressText
        val port = server?.port
        return if (addr != null && port != null) "http://$addr:$port/" else null
    }

    /** 当前配对地址（含一次性 token，二维码内容与副卡地址行同源）；未运行返回 null。 */
    fun pairingUrl(): String? = pairingUrlText

    /** 当前配对 token 是否有效（配对成功消费后为 false）。 */
    fun isPairingTokenActive(): Boolean = tokens.isActive

    /** 当前远程文本快照（键盘重建时补渲染用）；无内容返回 null。 */
    fun remoteTextSnapshot(): String? = remoteBuffer.text.takeIf { it.isNotEmpty() }

    /**
     * 启动服务：解析默认路由 IPv4 → 随机端口绑定 → 签发一次性 token → 进入监听。
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
            tokens.issue() // FR-B10：一次性 token，每轮服务会话新签发
            mainPageCache = null // 页面缓存随服务会话失效：资产更新后本次启动即生效
            val tvName = resolveTvName()
            val instance = RemoteInputServer(
                address,
                0, // 端口随机：requestedPort 默认 0（决策 16）
                wsDelegateFactory = {
                    // T15：每连接独立会话实例（配对态不跨连接）；业务回调经缓冲 + 主线程派发
                    PairingSession(
                        tokens = tokens,
                        tvName = tvName,
                        logEvent = { Log.i(TAG, it) },
                        onInput = ::onRemoteInput,
                        onClear = ::onRemoteClear,
                    )
                },
                mainPageProvider = { loadMainPage(context) },
            )
            val result = instance.start()
            when (result) {
                is RemoteInputServer.StartResult.Started -> {
                    server = instance
                    addressText = address.hostAddress
                    pairingUrlText = "http://${address.hostAddress}:${result.port}/?token=${tokens.peek()}"
                    // 验收通道：端口与配对地址经此条日志获知（adb logcat -s RemoteInputServer，仅 debug 构建）
                    Log.i(TAG, "远程输入服务已启动：${addressUrl()}；配对地址：${pairingUrlText}（WS 端点同端口 /ws）")
                    onPairingStateChanged?.invoke()
                }
                is RemoteInputServer.StartResult.Failed ->
                    Log.w(TAG, "启动失败：${result.reason}")
            }
            return result
        }
    }

    /** 停止服务并确认无端口残留；token、配对地址与远程输入缓冲一并清空。返回停止前是否在运行。幂等。 */
    fun stop(): Boolean {
        synchronized(this) {
            val instance = server ?: return false
            server = null
            addressText = null
            pairingUrlText = null
            tokens.clear()
            instance.stop()
            Log.i(TAG, "远程输入服务已停止，监听已关闭，配对 token 已作废")
            // 远程缓冲会话结束即清（第 8 章隐私），UI 回键盘态（完整退出流程随 T17）
            remoteBuffer.clear()
            mainHandler.post { onRemoteTextChanged?.invoke(null) }
            onPairingStateChanged?.invoke()
            return true
        }
    }

    // ---- T15 · 业务回调与页面下发 ----

    /** input 消息（WS 读线程）：缓冲兜底限长后经主线程通知 UI；内容不入日志（隐私红线）。 */
    private fun onRemoteInput(text: String) {
        val stored = remoteBuffer.set(text)
        mainHandler.post { onRemoteTextChanged?.invoke(stored) }
    }

    /** clear 消息（WS 读线程）：清缓冲并通知 UI 回空态。 */
    private fun onRemoteClear() {
        remoteBuffer.clear()
        mainHandler.post { onRemoteTextChanged?.invoke(null) }
    }

    /**
     * H5 页字节（决策 20）：读 APK assets `remote/index.html`，服务运行期内驻内存缓存；
     * 每次服务启动先失效缓存（页面资产更新后随启停生效）；assets 缺失 / 读取失败回退
     * T14 占位页并记事件（页面内容与用户输入无关，可缓存）。
     */
    private fun loadMainPage(context: Context): ByteArray {
        mainPageCache?.let { return it }
        return try {
            context.assets.open(MAIN_PAGE_ASSET).use { input -> input.readBytes() }
                .also { mainPageCache = it }
        } catch (e: Exception) {
            Log.w(TAG, "H5 页读取失败（${e.javaClass.simpleName}），回退占位页")
            PlaceholderPage.HTML.toByteArray(java.nio.charset.StandardCharsets.UTF_8)
        }
    }

    /** TV 设备名（PRD 第 7 章 state.tvName）：取系统型号；空值兜底，避免下发空串。 */
    private fun resolveTvName(): String =
        Build.MODEL?.trim()?.takeIf { it.isNotEmpty() } ?: "Android TV"

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

// T13 · 远程输入服务启停生命周期（TASKS T13：「随远程模式启停、退出端口无残留」）。
// 远程模式 UI 属 T15/T17：届时远程模式进入（FR-B2）调 start、退出（FR-B7）调 stop。
// T13 阶段走查入口为 debug 广播接收器（app/src/debug，决策 21 同口径：release 不暴露）。
// T14 增量（FR-B4/B10）：启动时签发一次性配对 token，/ws 装配 hello/state 配对会话；
// 配对地址（含 token）随启动日志输出供走查，仅 debug 构建可达；token 与服务同生命周期，停止即清空。
// T15 增量（FR-C4/B3/B5）：GET / 下发 APK assets 内 H5 页（决策 20）；/ws 以会话工厂按连接
// 创建配对会话，input/clear 业务回调经主线程 Handler 通知 UI（onRemoteTextChanged）；
// 远程文本缓冲仅驻内存（RemoteTextBuffer，200 字码点上限兜底），停止即清空、日志不涉内容。
// 本层是唯一的 android.* 依赖面：地址收集（ConnectivityManager / NetworkInterface）、TV 名与日志。
// T16 增量（FR-B6/C6 · 决策 9/10）：commit 上屏闭环——commit 经主线程交给 IME 写入
//（onCommitRequested → InputConnection 光标处插入），写入结果经 ack 回发 committed 给手机端；
// 写入成功即 stop()：关服务、清缓冲、自动收起远程模式回键盘态（FR-B6 / R-4）。
// T17 增量（FR-B9 / 决策 32/34）：配对成功经 onPaired 驻留 hello.deviceName（会话内存，
// 停止即清、不持久化、不入日志）并通知 UI 刷新状态机——服务运行 / token 消费 / 设备名
// 三项可观测值是连接状态（未连接 / 等待扫码 / 已连接）的单一事实源。
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

    /**
     * 配对设备名（T17 · FR-B9 / 决策 34）：hello.deviceName 会话内存驻留供状态行展示——
     * 仅驻内存、服务停止即清、不持久化、不入日志（隐私红线不破）；写入发生在 WS 读线程（volatile）。
     */
    @Volatile
    private var pairedDeviceName: String? = null

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

    /**
     * 上屏写入回调（T16 · FR-B6/C6）：commit 上屏指令经主线程交 IME 执行——
     * 在电视输入框光标处插入（不覆盖其余文字，有选中区间则替换该区间，InputConnection
     * commitText 框架语义）。返回 true = 写入成功；false = 写入失败（如无焦点输入框）。
     * 由 [SofatypeImeService] 注册；回调在主线程执行，内容仅经内存传递，任何日志不记录。
     */
    @Volatile
    var onCommitRequested: ((String) -> Boolean)? = null

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

    /** 已配对设备名（T17 · 决策 34）：hello 携带的 deviceName，会话内存驻留；未配对 / 已停止为 null。 */
    fun pairedDeviceName(): String? = pairedDeviceName

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
            pairedDeviceName = null // 决策 34：设备名随会话，新一轮启动不残留上一轮值
            mainPageCache = null // 页面缓存随服务会话失效：资产更新后本次启动即生效
            val tvName = resolveTvName()
            val instance = RemoteInputServer(
                address,
                0, // 端口随机：requestedPort 默认 0（决策 16）
                wsDelegateFactory = {
                    // T15：每连接独立会话实例（配对态不跨连接）；业务回调经缓冲 + 主线程派发；
                    // T16：commit 上屏指令经 onRemoteCommit 派发（写入 + 回执 + 成功停服）；
                    // T17（FR-B9 / 决策 34）：配对成功驻留 deviceName 并通知 UI 刷新状态机。
                    // 回调在 WS 读线程：volatile 写安全，UI 通知经主线程 Handler 派发。
                    PairingSession(
                        tokens = tokens,
                        tvName = tvName,
                        logEvent = { Log.i(TAG, it) },
                        onInput = ::onRemoteInput,
                        onClear = ::onRemoteClear,
                        onCommit = ::onRemoteCommit,
                        onPaired = ::onPaired,
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

    /** 停止服务并确认无端口残留；token、配对地址、设备名与远程输入缓冲一并清空。返回停止前是否在运行。幂等。 */
    fun stop(): Boolean {
        synchronized(this) {
            val instance = server ?: return false
            server = null
            addressText = null
            pairingUrlText = null
            tokens.clear()
            pairedDeviceName = null // 决策 34：设备名会话结束即清（仅驻内存，不持久化）
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

    /**
     * 配对成功（T17 · FR-B9 / 决策 34，WS 读线程）：deviceName 会话内存驻留（volatile 写），
     * UI 通知经主线程 Handler 派发到 [onPairingStateChanged]（副卡切已连接卡面 / 主卡状态行切绿点）。
     * 名字仅驻内存展示，不入日志（隐私红线）。
     */
    private fun onPaired(deviceName: String?) {
        pairedDeviceName = deviceName
        mainHandler.post { onPairingStateChanged?.invoke() }
    }

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
     * commit 消息（T16 · FR-B6/C6，WS 读线程）：经主线程执行上屏——
     * ① IME 在电视输入框光标处插入（onCommitRequested；null = 键盘侧未就绪，按失败处理）；
     * ② 写入结果经 ack 回发 committed { ok } 给手机端（成功后手机清草稿并提示「已上屏」）；
     * ③ 写入成功即 [stop]：关服务、清缓冲，TV 端自动收起远程模式回键盘态（R-4）。
     * 写入失败（无焦点输入框）保持远程模式与缓冲，手机端提示失败可重试；
     * 断连与退出异常路径的完整清理属 T18，本任务不动其边界。
     * 内容不入日志（隐私红线）。
     */
    private fun onRemoteCommit(text: String, ack: (Boolean) -> Unit) {
        mainHandler.post {
            val ok = onCommitRequested?.invoke(text) ?: false
            ack(ok) // 回执必须先于 stop：stop 关闭连接后回发不可达
            if (ok) {
                stop()
            }
        }
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

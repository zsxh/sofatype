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
// ~~写入成功即 stop()：关服务、清缓冲、自动收起远程模式回键盘态（FR-B6 / R-4）~~
//（→ 被决策 59 / T70 取代：写入成功不再停服，见下方 T70 增量）。
// T17 增量（FR-B9 / 决策 32/34）：配对成功经 onPaired 驻留 hello.deviceName（会话内存，
// 停止即清、不持久化、不入日志）并通知 UI 刷新状态机。
// T35 增量（决策 35 · FR-B11 提前 / FR-B8 修订）：token 滚动签发——配对成功即签发新 token
// 并刷新配对地址（副卡二维码重渲染出新码，逐 token 一次性语义不变）；新设备配对成功自动
// 顶替旧连接（旧连接回发 replaced 后按 CLOSE_REPLACED 关闭），已配对连接断开经
// PairedSessionRegistry 登记 / 清除驱动 UI 回等待扫码卡（新码仍有效）。「已连接」可观测值
// 由「token 已消费」改为「存在已配对会话」。
// T18 增量（FR-B7/B8/C8 · 第 8 章隐私/可靠性 · lead 拍板 2026-11-07 三项）：
// ① TV 退出先通知手机（FR-B7）：stop() 在有已配对会话时先向手机回发 disconnect { reason }
//  （PRD 第 7 章双向）并按 1000 关连接，随后才停服关监听——通知写经专用 teardown 线程
//  执行（同步等待写完，主线程不阻塞；帧写仍走各连接单线程 ConnectionWriter）。
// ② commit 回执与停服移出主线程（T16 评审遗留评估项落地）：主线程只执行 IME 写入，
//  ack + 停服在 teardown 线程串行执行——原「主线程同步等待回执写完成上限 2s」阻塞消除，
//  「回执先于停服」时序由单线程 teardown 保住。
// ③ 已配对连接断开（非被顶替）即清远程缓冲（第 8 章隐私：会话结束即清除）+ 主卡状态行
//  瞬时呈现「连接已断开」（FR-B8 原文，约 3s 后回决策 32 锁定的青点「等待手机扫码」，
//  Q3 lead 拍板形式）；副卡回等待扫码卡沿 T35 既有链路不变。
// ④ state 增配对地址字段（Q1 lead 拍板 A）：经 [PairingSession.pairingUrlProvider] 下发
//  滚动新码，手机页刷新即可重连；FR-B10 逐 token 一次性不变。
// ⑤ 网络变化自动重建（Q2 lead 拍板 A · PRD 5.3「二维码 IP 失效时自动重建服务并刷新二维码」）：
//  服务运行期注册 ConnectivityManager 回调——默认网络丢失后恢复，或默认网络 IPv4 与绑定地址
//  不一致（不同网段）时，自动 stop（尽力通知）+ start 重建服务并刷新二维码；仅 debug 构建
//  路径内注册（start 即 debug 门控），重建走与手动退出同一通知链路。
// T25 增量（第 8 章电量与资源 P1）：远程模式空闲自动退出——空闲口径按 PRD 括注 = 无手机连接。
//  服务启动成功（进入远程模式）与已配对连接断开（回到等待扫码）时起算空闲窗口；手机已连接
//  期间与停服后解除跟踪。窗口满阈值（判定单一事实源 RemoteIdlePolicy，正式 30 分钟）经
//  onIdleDeadlineReached 复核运行态后走 [stop] 既有完整清理链路（通知手机 / 关监听 /
//  清 token / 缓冲 / 设备名），并经 onRemoteIdleExpired 主线程通知 IME 收起键盘。
//  排程在主线程（mainHandler.postDelayed），任何线程的进入/解除统一 post 到主线程串行化；
//  到期回调以任务身份比对作废陈旧排程（同 T22 连删 job 模式），并以运行态 + 已配对会话
//  复核竞态（连接与到期同刻竞争时不误退）。
// T70 增量（决策 59 · 远程输入上屏保活）：
// ① commit 上屏成功不再 stop()——处置收口 RemoteCommitAftermath（单一事实源）：回执
//  committed { ok:true } 后清空远程缓冲并经主线程通知 UI（大字区清空、远程模式与连接保持，
//  状态行保持「已连接」）；写入失败路径不变（远程模式与缓冲保持，手机端提示失败可重试）。
//  停服仅由显式退出（FR-B7）、空闲到期、网络重建触发。
// ② 新增「已连接无活动」空闲跟踪（判定单一事实源 RemoteConnectedIdlePolicy，正式 10 分钟）：
//  连接保持期间无任何 WS 消息收发满阈值 → 走既有 [stop] 完整清理链路（先向手机回发
//  disconnect { reason:"tv-exit" } 再按 1000 关连接后停服，FR-B7 通知链）+ onRemoteIdleExpired
//  收起键盘。活动口径 = 已配对会话上的协议消息收发（onPaired / onRemoteInput / onRemoteClear /
//  onRemoteCommit 处登记）；排程沿用主线程 post 串行化 + 任务身份比对（T22/T25 模式），
//  到期以运行态 + 已配对会话复核竞态（断连与到期同刻不误退，回退交 T25 无连接空闲口径）。
//  与 T25「无手机连接」30 分钟空闲并存互不干扰：有连接看本跟踪，无连接看 T25。
//  验证通道 debugConnectedIdleExpire()（决策 21 debug 门控）：活动时刻回拨一个完整窗口后
//  立即到期，走与真实到期完全相同的判定与清理路径（正式阈值不改动）。
// ③ 断连清缓冲、回等待扫码卡、网络重建、token 滚动签发等既有链路不改。
// T71 · 决策 60：本类与 PairingSession 的全部日志点机械迁移到 Timber（Timber.tag(TAG)，
// TAG=RemoteInputServer 既有口径不变、文案不变；logcat 树 DebugTree 输出与迁移前一致）。
// T72 增量（决策 61 · 远程输入连接代号）：hello 增 deviceType / deviceKey（PairingSession 上送），
// 配对成功经 ClientCodeRegistry 签发 / 沿用序号（LRU 内存表：TTL 15 分钟滚动续期、容量 8、
// 满淘汰最久未见、匿名签发仅当次连接固定）；state 经 clientCodeProvider 下发 clientCode
// （state 为代号唯一事实源）；WS 活动（input / clear / commit）滚动续期 lastSeen。
// 注册表随进程驻留：停服不清表（保号窗口跨 TV 退出持续，断开后 15 分钟内重连保号），
// 仅钉住键随会话切换 / 停服解除（连接中代号永不变）。deviceKey 与序号仅内存驻留，
// 不持久化、不入日志（隐私红线，决策 61）。
// T75 增量（决策 63 · 远程服务端口持久复用）：端口来源策略化，判定收口 RemotePortPolicy
//（纯逻辑，JVM 单测覆盖）——每轮启动优先复用上次持久化端口；复用被占（BindException）→
// 动态段 49152–65535 重新随机并持久化新值，有限重试（3 次）仍失败按启动失败明示原因、
// 不静默放弃；随机绑定成功即持久化新端口号。持久化走 SharedPreferences 适配
//（RemoteServicePortStore，仅端口号一个配置项，无用户输入内容与身份信息，T71 先例口径，
// 跨停服 / 跨进程重启保留）。服务绑定层 setReuseAddress(true) 见 RemoteInputServer。
// 协议与 H5 零改动；网络重建路径共用 startInternal，重建后端口亦复用持久化值（决策 63
// 残余边界 ③：网络变化自动重建后端口不变）。
package com.sofatype.ime.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.sofatype.ime.BuildConfig
import timber.log.Timber
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketException
import java.util.concurrent.Executors

object RemoteInputServerManager {
    private const val TAG = "RemoteInputServer"

    /** H5 页 assets 路径（决策 20：打进 APK assets 由本服务下发）。 */
    private const val MAIN_PAGE_ASSET = "remote/index.html"

    @Volatile
    private var server: RemoteInputServer? = null

    @Volatile
    private var addressText: String? = null

    /** 一次性配对 token（FR-B10 · 决策 35 滚动签发）：每轮启动签发，配对成功后再签发新值，停止即清空。 */
    private val tokens = PairingTokenStore()

    /**
     * 已配对会话登记（T35 · 决策 35）：当前唯一已配对连接的身份与控制句柄（回发 / 关闭），
     * 供新设备配对成功时顶替旧连接；读写仅在 Manager synchronized 区（WS 读线程进入）。
     */
    private val pairedSessions = PairedSessionRegistry()

    /**
     * 各连接控制句柄缓存（T35）：会话实例 → 回发 / 关闭通道（WS 升级完成即登记，
     * 会话结束 / 服务停止即清），读写仅在 Manager synchronized 区。
     */
    private val sessionControls = HashMap<PairingSession, PairedConnection>()

    /** replaced 顶替通知载荷（T35 · 决策 35，TV → 旧手机）。 */
    private const val REPLACED_MESSAGE = "{\"type\":\"${PairingSession.MSG_REPLACED}\"}"

    /** TV 退出远程的断连通知载荷（T18 · FR-B7 / PRD 第 7 章 disconnect 双向，TV → 手机）。 */
    private const val DISCONNECT_TV_EXIT_MESSAGE =
        "{\"type\":\"${PairingSession.MSG_DISCONNECT}\",\"reason\":\"${PairingSession.REASON_TV_EXIT}\"}"

    /** 网络变化重建的断连通知载荷（R2 · PRD 5.3 行 2 / lead 批准 reason 取值增量，TV → 手机）。 */
    private const val DISCONNECT_NETWORK_REBUILD_MESSAGE =
        "{\"type\":\"${PairingSession.MSG_DISCONNECT}\",\"reason\":\"${PairingSession.REASON_NETWORK_REBUILD}\"}"

    /** TV 退出远程关闭已配对连接的 WS 关闭码（RFC 6455 正常关闭）。 */
    private const val CLOSE_NORMAL_EXIT = 1000

    /**
     * 「连接已断开」瞬时提示时长（ms，T18 · FR-B8 / Q3 lead 拍板）：已配对连接断开时主卡
     * 状态行短暂呈现该提示，到期自动回决策 32 锁定的青点「等待手机扫码」终态。
     */
    private const val DISCONNECT_NOTICE_MS = 3_000L

    /**
     * 停服 / 通知编排队列（T18）：单线程串行执行「向手机回发 disconnect → 关连接 → 停服」
     * 与「commit 回执 → 停服」序列——网络写（同步等待写完，上限 2s）不落主线程
     * （T16 评审遗留评估项落地），时序依赖由单线程 FIFO 保住；daemon 线程不阻碍进程退出。
     */
    private val teardown = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "sofatype-remote-teardown").apply { isDaemon = true }
    }

    /** 停服编排进行中标记（防重入：编排窗口内的重复 stop 直接返回 false）。 */
    @Volatile
    private var stopping = false

    /** 用户 / 外部主动退出标记（含 commit 成功停服、debug 广播）：网络重建任务据此放弃复活服务。 */
    @Volatile
    private var exitRequested = false

    /** 网络重建进行中标记（防多网络事件并发触发重建）。 */
    @Volatile
    private var rebuilding = false

    /**
     * 「连接已断开」提示截止时刻（epoch ms，T18 · FR-B8 / Q3 lead 拍板）：> 当前时刻 =
     * 瞬时提示呈现中；0 = 无提示。仅主线程写（mainHandler 派发），volatile 读安全。
     */
    @Volatile
    private var disconnectNoticeUntil = 0L

    /** 默认网络丢失标记（T18 · 网络重建）：丢失时置位，恢复后的首个网络事件触发重建。 */
    @Volatile
    private var rebuildAfterNetworkLoss = false

    /** 网络变化回调（T18 · Q2 lead 拍板 A）：服务运行期注册，停止即解注册。 */
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    /** 网络重建用 Context（start 时的 applicationContext；重建 start 复用）。 */
    @Volatile
    private var watcherContext: Context? = null

    /**
     * 配对设备名（T17 · FR-B9 / 决策 34）：hello.deviceName 会话内存驻留供状态行展示——
     * 仅驻内存、服务停止即清、不持久化、不入日志（隐私红线不破）；写入发生在 WS 读线程（volatile）。
     */
    @Volatile
    private var pairedDeviceName: String? = null

    /**
     * 当前连接的保号键（T72 · 决策 61：hello.deviceKey，WS 读线程写、volatile 读）。
     * 仅内存驻留，停服 / 断连即清、不入日志（隐私红线）。
     */
    @Volatile
    private var pairedDeviceKey: String? = null

    /**
     * 当前连接的连接代号（T72 · 决策 61：配对成功时经注册表签发 / 沿用），
     * 供状态机渲染胶囊与 state.clientCode 下发（state 为代号唯一事实源）。
     */
    @Volatile
    private var pairedClientCode: ClientCode? = null

    /**
     * 连接代号序号注册表（T72 · 决策 61）：LRU 内存表 deviceKey→(类型, 序号, lastSeen)。
     * 随进程驻留、停服不清表（保号窗口跨 TV 退出 / 服务重启持续，进程死亡才消亡）；
     * 写入经注册表内部 synchronized（WS 读线程与主线程多点触达）。
     */
    private val clientCodes = ClientCodeRegistry()

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

    /**
     * 空闲自动退出回调（T25 · 第 8 章电量资源）：空闲到期自动停服（既有完整清理链路）后
     * 经主线程通知 IME 收起键盘（远程模式框架已随停服回键盘态，此处收起整个键盘窗口）。
     * 由 [SofatypeImeService] 注册，生命周期同其他回调（onCreateInputView 注册 / onDestroy 解除）。
     */
    @Volatile
    var onRemoteIdleExpired: (() -> Unit)? = null

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

    /** 当前配对 token 是否有效（决策 35 滚动签发：启动与每次配对成功后均为 true；供诊断用）。 */
    fun isPairingTokenActive(): Boolean = tokens.isActive

    /** 当前是否存在已配对会话（T35 · 决策 35：UI 状态机「已连接」判定源，volatile 读安全）。 */
    fun hasPairedSession(): Boolean = pairedSessions.hasPairedSession

    /** 已配对设备名（T17 · 决策 34）：hello 携带的 deviceName，会话内存驻留；未配对 / 已停止为 null。 */
    fun pairedDeviceName(): String? = pairedDeviceName

    /**
     * 当前连接的连接代号（T72 · 决策 61）：配对成功时签发 / 沿用（如「微信设备 01」）；
     * 未配对 / 已停止为 null。UI 状态机胶囊与 state.clientCode 同源此处（代号唯一事实源）。
     */
    fun pairedClientCode(): ClientCode? = pairedClientCode

    /** 当前远程文本快照（键盘重建时补渲染用）；无内容返回 null。 */
    fun remoteTextSnapshot(): String? = remoteBuffer.text.takeIf { it.isNotEmpty() }

    /**
     * 「连接已断开」瞬时提示是否呈现中（T18 · FR-B8 / Q3 lead 拍板）：已配对连接断开后
     * 3s 内为 true，终态仍为决策 32 锁定的青点「等待手机扫码」。主线程读取（渲染）。
     */
    fun disconnectNoticeActive(): Boolean = disconnectNoticeUntil > System.currentTimeMillis()

    /**
     * 启动服务：解析默认路由 IPv4 → 随机端口绑定 → 签发一次性 token → 进入监听。
     * debug 与 release 构建均可用（T79 · 决策 65：release 门控放开）。
     * 失败只返回结果，不抛异常（第 8 章可靠性：不得影响 IME 进程）。
     * 显式启动 = 新的会话意图：清除退出标记（网络重建路径走 [startInternal]，不清标记）。
     */
    fun start(context: Context): RemoteInputServer.StartResult {
        synchronized(this) {
            exitRequested = false
        }
        return startInternal(context)
    }

    /** 启动内部实现（start 与网络重建共用；不改变退出标记）。 */
    private fun startInternal(context: Context): RemoteInputServer.StartResult {
        synchronized(this) {
            if (isRunning()) {
                return RemoteInputServer.StartResult.Failed("服务已在运行（${addressUrl()}）")
            }
            val address = resolveLanAddress(context)
            if (address == null) {
                val reason = "未找到可绑定的局域网 IPv4 接口"
                Timber.tag(TAG).w("启动失败：$reason")
                return RemoteInputServer.StartResult.Failed(reason)
            }
            tokens.issue() // FR-B10：一次性 token，每轮服务会话新签发
            pairedDeviceName = null // 决策 34：设备名随会话，新一轮启动不残留上一轮值
            pairedDeviceKey = null // T72：保号键随会话（序号注册表不清，保号窗口跨停服持续）
            pairedClientCode = null // T72：代号随会话，新一轮启动不残留上一轮值
            clientCodes.unpin() // T72：新会话无已配对连接，解除上一轮钉住
            disconnectNoticeUntil = 0 // T18：新一轮会话不残留上一轮断连提示
            exitRequested = false // T18：新会话清除上一轮退出标记（重建路径据此放行）
            mainPageCache = null // 页面缓存随服务会话失效：资产更新后本次启动即生效
            val tvName = resolveTvName()
            // T75（决策 63）端口策略化：按策略解析端口——优先复用持久化端口，被占（BindException）
            // 动态段重新随机、有限重试；随机绑定成功即持久化。每个候选端口各起一个独立实例
            //（requestedPort 构造后不可变），仅绑定成功实例保留进入运行态，失败实例就地弃置。
            var startedServer: RemoteInputServer? = null
            var startedPort = -1
            val assignment = RemotePortPolicy(RemoteServicePortStore(context)).resolve { candidate ->
                val attemptServer = buildRemoteInputServer(address, tvName, context, candidate)
                when (val result = attemptServer.start()) {
                    is RemoteInputServer.StartResult.Started -> {
                        startedServer = attemptServer
                        startedPort = result.port
                        PortBindAttempt.Bound(result.port)
                    }
                    is RemoteInputServer.StartResult.Failed ->
                        if (result.portInUse) {
                            PortBindAttempt.Occupied // 决策 63：被占 → 换新随机端口重试
                        } else {
                            PortBindAttempt.Unavailable(result.reason) // 非占用失败 → 原样终止不静默
                        }
                }
            }
            val instance = startedServer
            if (instance == null) {
                val reason = (assignment as? PortAssignment.Failed)?.reason ?: "端口分配失败（未知原因）"
                Timber.tag(TAG).w("启动失败：$reason")
                return RemoteInputServer.StartResult.Failed(reason)
            }
            when (assignment) {
                is PortAssignment.Reused ->
                    Timber.tag(TAG).i("端口复用命中：$startedPort（决策 63 持久化端口，跨停服 / 进程重启保留）")
                is PortAssignment.Randomized ->
                    Timber.tag(TAG).i(
                        assignment.previousPort
                            ?.let { "持久化端口 $it 被占，动态段换新随机端口：$startedPort（已持久化新值）" }
                            ?: "首次启动动态段随机取得端口：$startedPort（已持久化，后续轮复用）",
                    )
                else -> Unit // 失败分支上方已 return，防御不可达
            }
            server = instance
            addressText = address.hostAddress
            pairingUrlText = "http://${address.hostAddress}:${startedPort}/?token=${tokens.peek()}"
            watcherContext = context.applicationContext
            registerNetworkWatcher(context.applicationContext) // T18：网络变化自动重建（运行期生效）
            // T25：进入远程模式即处于「无手机连接」态，空闲窗口自此起算
            armIdleExit(System.currentTimeMillis())
            // 验收通道：端口与配对地址经此条日志获知（adb logcat -s RemoteInputServer，仅 debug 构建）
            Timber.tag(TAG).i("远程输入服务已启动：${addressUrl()}；配对地址：${pairingUrlText}（WS 端点同端口 /ws）")
            // T18：start 可经网络重建在 teardown 线程进入，UI 回调统一主线程派发
            mainHandler.post { onPairingStateChanged?.invoke() }
            return RemoteInputServer.StartResult.Started(startedPort)
        }
    }

    /**
     * 构建远程输入服务实例（T75 · 决策 63 端口策略化抽出的工厂）：每个候选端口各建一个实例，
     * [requestedPort] 由 RemotePortPolicy 解析后传入（原决策 16 内联固定 0 随机口径随之退役）。
     * 会话装配与原 startInternal 内联构造逐行一致：token 会话、业务回调、主线程派发、
     * 滚动配对地址与连接代号下发均不变。
     */
    private fun buildRemoteInputServer(
        address: InetAddress,
        tvName: String,
        context: Context,
        requestedPort: Int,
    ): RemoteInputServer =
        RemoteInputServer(
            address,
            requestedPort,
            wsDelegateFactory = {
                // T15：每连接独立会话实例（配对态不跨连接）；业务回调经缓冲 + 主线程派发；
                // T16：commit 上屏指令经 onRemoteCommit 派发（写入 + 回执 + 成功停服）；
                // T17（FR-B9 / 决策 34）：配对成功驻留 deviceName 并通知 UI 刷新状态机。
                // T35（决策 35）：配对成功滚动签发新 token + 顶替旧连接；已配对连接断开
                // 清登记并回等待扫码卡。回调在连接线程：volatile 写安全，
                // UI 通知经主线程 Handler 派发。会话实例先创建再闭包捕获（作登记身份键）。
                // T18（Q1 lead 拍板 A）：state 经 pairingUrlProvider 下发滚动新码配对地址
                //（hello 校验通过后才取值，被拒路径不消费；FR-B10 一次性不变）。
                // T72（决策 61）：onPaired 扩参上送 deviceType / deviceKey 供序号签发；
                // state 经 clientCodeProvider 下发连接代号（hello 校验通过后才取值）。
                lateinit var session: PairingSession
                session = PairingSession(
                    tokens = tokens,
                    tvName = tvName,
                    logEvent = { Timber.tag(TAG).i(it) },
                    onInput = ::onRemoteInput,
                    onClear = ::onRemoteClear,
                    onCommit = ::onRemoteCommit,
                    onPaired = { deviceName, deviceType, deviceKey ->
                        onPaired(session, deviceName, deviceType, deviceKey)
                    },
                    onPairedSessionClosed = { onPairedSessionClosed(session) },
                    onConnectionAttached = { sender, close ->
                        onPairedConnectionAttached(session, sender, close)
                    },
                    pairingUrlProvider = { pairingUrlText },
                    clientCodeProvider = { pairedClientCode?.text },
                )
                session
            },
            mainPageProvider = { loadMainPage(context) },
        )

    /**
     * 停止服务（T18 · FR-B7 重写）：存在已配对会话时先向手机回发 disconnect { reason }
     * （TV 退出远程通知，验收 2）并按 1000 关连接，随后停服；无已配对会话直接停服。
     * 通知的网络写在专用 teardown 线程执行（主线程不阻塞；写完成才停服，保证通知可达），
     * 本方法对调用方立即返回（返回停止前是否在运行）。幂等；token、配对地址、设备名与
     * 远程输入缓冲在停服时一并清空。
     */
    fun stop(): Boolean {
        val notifyTarget: PairedConnection?
        synchronized(this) {
            // 退出意图无条件记录（含服务已停的冗余调用）：网络重建任务据此放弃复活服务；
            // 显式 start 会清除该标记（新会话意图）
            exitRequested = true
            if (server == null || stopping) return false
            stopping = true // 编排窗口防重入
            notifyTarget = pairedSessions.currentConnection()
        }
        if (notifyTarget == null) {
            stopNow()
            return true
        }
        teardown.execute {
            // 通知必须先于停服：停服关连接后回发不可达。写失败（对端已死 / 网络已断）静默
            runCatching { notifyTarget.sender.send(DISCONNECT_TV_EXIT_MESSAGE) }
            runCatching { notifyTarget.close(CLOSE_NORMAL_EXIT) }
            stopNow()
        }
        return true
    }

    /**
     * 停服实际清理（T18）：关监听与全部连接、清 token / 登记表 / 控制句柄 / 设备名 /
     * 远程缓冲（第 8 章隐私：会话结束即清除）、解注册网络回调，UI 回键盘态。幂等。
     */
    private fun stopNow() {
        synchronized(this) {
            stopping = false
            // R2（评审建议 2 采纳）：rebuilding 不在此清零——其生命周期收口到重建任务自身结束处，
            // 防止重建窗口内的 stopNow 提前解除防重入（teardown FIFO 保持时序；用户退出路径由
            // 重建任务的 exitRequested 分支收口）
            rebuildAfterNetworkLoss = false
            disconnectNoticeUntil = 0
            unregisterNetworkWatcher()
            watcherContext = null
            val instance = server ?: return
            server = null
            addressText = null
            pairingUrlText = null
            tokens.clear()
            pairedSessions.clear() // T35：已配对会话登记随服务会话结束（各连接由 stop 关闭后迟到的清除回调幂等）
            sessionControls.clear()
            pairedDeviceName = null // 决策 34：设备名会话结束即清（仅驻内存，不持久化）
            pairedDeviceKey = null // T72：保号键会话结束即清（序号注册表不清，保号窗口持续）
            pairedClientCode = null // T72：代号会话结束即清
            clientCodes.unpin() // T72：解除钉住（注册表保留，断开后 15 分钟窗口内重连保号）
            cancelIdleExit() // T25：远程模式结束，撤销空闲退出排程（手动退出 / 上屏停服 / 空闲到期自退共用）
            cancelConnectedIdleWatch() // T70：远程模式结束，撤销「已连接无活动」排程（幂等）
            instance.stop()
            Timber.tag(TAG).i("远程输入服务已停止，监听已关闭，配对 token 已作废")
            // 远程缓冲会话结束即清（第 8 章隐私），UI 回键盘态。
            // T18：stop 编排已移至 teardown 线程（FR-B7 通知先于停服），UI 回调必须经
            // mainHandler 派发——本线程直调会在非主线程碰视图（实测 CalledFromWrongThreadException
            // 进程崩溃，见 issue T18 坑 1）；两条回调均为主线程契约。
            remoteBuffer.clear()
            mainHandler.post { onRemoteTextChanged?.invoke(null) }
            mainHandler.post { onPairingStateChanged?.invoke() }
        }
    }

    // ---- T15 · 业务回调与页面下发 ----

    /**
     * 配对成功（T35 · 决策 35 重写，T72 · 决策 61 扩参，连接线程）：
     * ① 新连接登记进已配对会话表，交出被顶替的旧连接——旧连接先回发 replaced 通知再按
     *    CLOSE_REPLACED 关闭（FR-B11 自动顶替；旧手机端显示「连接已被替换」进未连接态）；
     * ② 滚动签发新 token 并刷新配对地址（副卡二维码随 UI 刷新重渲染出新码，
     *    逐 token 一次性语义不变，FR-B10）；已连接态展示的码 = 新签发的有效码；
     * ③ deviceName 驻留（决策 34：会话内存，不持久化、不入日志）；
     * ④ 连接代号签发 / 沿用（决策 61）：经注册表按 deviceKey 签发序号（命中沿用、未命中
     *    max+1、匿名当次固定），钉住保号键（连接中代号永不变），代号随 state.clientCode
     *    下发（唯一事实源）并驱动两端胶囊展示。
     * UI 刷新经主线程 Handler 派发到 [onPairingStateChanged]（副卡二维码卡 + 状态行 /
     * 主卡状态行绿点切连接代号胶囊）。服务已停止的迟到回调按防御忽略。
     */
    private fun onPaired(session: PairingSession, deviceName: String?, deviceType: DeviceType, deviceKey: String?) {
        synchronized(this) {
            if (!isRunning()) return
            val connection = sessionControls[session] ?: return
            val replaced = pairedSessions.attach(session, connection)
            replaced?.let { old ->
                Timber.tag(TAG).i("新设备配对成功，顶替旧连接")
                runCatching { old.sender.send(REPLACED_MESSAGE) }
                runCatching { old.close(PairingSession.CLOSE_REPLACED) }
            }
            val addr = addressText
            val port = server?.port
            if (addr != null && port != null) {
                val token = tokens.issue() // 决策 35：滚动签发，旧 token 已消费，新码供下一台设备
                pairingUrlText = "http://$addr:$port/?token=$token"
                Timber.tag(TAG).i("配对 token 已滚动签发，二维码内容更新")
            }
            // T72（决策 61）：序号签发 / 沿用 + 钉住保号键（连接中代号永不变）；键值不入日志
            pairedClientCode = clientCodes.issue(deviceKey, deviceType, System.currentTimeMillis())
            pairedDeviceKey = deviceKey
            clientCodes.pin(deviceKey)
            pairedDeviceName = deviceName
            cancelIdleExit() // T25：手机已连接即退出空闲（空闲口径 = 无手机连接，连接期间不判空闲）
            markRemoteActivity() // T70：配对成功 = 首次 WS 活动，已连接无活动窗口自此起算
        }
        mainHandler.post { onPairingStateChanged?.invoke() }
    }

    /** 连接控制句柄登记（T35）：WS 升级完成即缓存该连接的回发 / 关闭通道，配对成功时取用。 */
    private fun onPairedConnectionAttached(
        session: PairingSession,
        sender: WsMessageSender,
        close: (Int) -> Unit,
    ) {
        synchronized(this) {
            sessionControls[session] = PairedConnection(sender, close)
        }
    }

    /**
     * 已配对会话关闭（T18 重写 · FR-B8 / 决策 35 断连回退，连接线程）：仅当断开者仍是登记中的
     * 当前会话时清除（被顶替旧连接的迟到关闭事件不清新登记），设备名一并清除；随后——
     * ① 清空远程缓冲（第 8 章隐私：输入会话结束即清除，不持久化；服务继续运行等待重新扫码）；
     * ② 主卡状态行瞬时呈现「连接已断开」（FR-B8 原文，Q3 lead 拍板形式），到期自动回
     *  决策 32 锁定的青点「等待手机扫码」（副卡回等待扫码卡沿 T35 既有链路不变）。
     * UI 通知经主线程 Handler 派发。
     */
    private fun onPairedSessionClosed(session: PairingSession) {
        val cleared = synchronized(this) {
            sessionControls.remove(session)
            pairedSessions.detachIfCurrent(session)
        }
        if (cleared) {
            pairedDeviceName = null
            pairedDeviceKey = null // T72：保号键随会话清（注册表条目保留，15 分钟窗口内重连保号）
            pairedClientCode = null // T72：代号随会话清
            clientCodes.unpin() // T72：解除钉住（重连时刻才可能刷新代号）
            remoteBuffer.clear() // 输入会话结束即清缓冲（第 8 章隐私；刷新重连后由手机端 input 重建显示）
            Timber.tag(TAG).i("已配对连接断开，回等待扫码态（配对码仍有效）")
            // T25：回到「无手机连接」态，空闲窗口自断开时刻重新起算
            armIdleExit(System.currentTimeMillis())
            // T70：已配对会话已结束，「已连接无活动」窗口一并解除（断连不触发该超时退出，
            // 回退交 T25 无连接空闲口径）
            cancelConnectedIdleWatch()
            mainHandler.post {
                // R2（评审建议 3 采纳）：延迟任务带时效快照——停服 / 新提示已覆盖该截止值时，
                // 到期任务不再重复清零与重渲染（快速断连-重连-再断连窗口不误清新提示）
                val noticeUntil = System.currentTimeMillis() + DISCONNECT_NOTICE_MS
                disconnectNoticeUntil = noticeUntil
                onRemoteTextChanged?.invoke(null)
                onPairingStateChanged?.invoke()
                mainHandler.postDelayed({
                    if (disconnectNoticeUntil == noticeUntil) {
                        disconnectNoticeUntil = 0
                        onPairingStateChanged?.invoke()
                    }
                }, DISCONNECT_NOTICE_MS)
            }
        }
    }

    /** input 消息（WS 读线程）：缓冲兜底限长后经主线程通知 UI；内容不入日志（隐私红线）。 */
    private fun onRemoteInput(text: String) {
        markRemoteActivity() // T70：业务消息到达 = WS 活动，已连接无活动窗口重新起算
        clientCodes.touch(pairedDeviceKey, System.currentTimeMillis()) // T72：代号 TTL 滚动续期
        val stored = remoteBuffer.set(text)
        mainHandler.post { onRemoteTextChanged?.invoke(stored) }
    }

    /** clear 消息（WS 读线程）：清缓冲并通知 UI 回空态。 */
    private fun onRemoteClear() {
        markRemoteActivity() // T70：业务消息到达 = WS 活动，已连接无活动窗口重新起算
        clientCodes.touch(pairedDeviceKey, System.currentTimeMillis()) // T72：代号 TTL 滚动续期
        remoteBuffer.clear()
        mainHandler.post { onRemoteTextChanged?.invoke(null) }
    }

    /**
     * commit 消息（T16 · FR-B6/C6，WS 读线程；T18 异步化 · T70 决策 59 保活重写）：
     * 主线程只执行 IME 上屏写入，回执移交 teardown 线程串行执行——
     * ① IME 在电视输入框光标处插入（onCommitRequested；null = 键盘侧未就绪，按失败处理）；
     * ② 处置结论收口 [RemoteCommitAftermath]（决策 59 单一事实源）：写入结果经 ack 回发
     *    committed { ok } 给手机端；
     * ③ 写入成功：**不再停服**（决策 59 废止 R-4 自动收起）——回执后清空远程缓冲并经主线程
     *    通知 UI 清大字区；WS 连接与远程模式保持、状态行保持「已连接」，手机端清草稿提示
     *    「已上屏」后可继续编辑、再次上屏（多输入框表单只扫一次码，内容落当前焦点输入框）；
     * ④ 写入失败：缓冲与远程模式保持，手机端提示失败可重试（FR-C6 既有口径不变）。
     * 停服仅由显式退出（FR-B7）、空闲到期（T25 / T70）、网络重建触发。
     * 时序保证：ack 先于缓冲清理与 UI 通知（单线程 teardown 顺序执行）；
     * 主线程不同步等待回执写完成（T18 既有异步化口径不变）。
     * 内容不入日志（隐私红线）。
     */
    private fun onRemoteCommit(text: String, ack: (Boolean) -> Unit) {
        markRemoteActivity() // T70：commit 到达 = WS 活动，已连接无活动窗口重新起算
        clientCodes.touch(pairedDeviceKey, System.currentTimeMillis()) // T72：代号 TTL 滚动续期
        mainHandler.post {
            val ok = onCommitRequested?.invoke(text) ?: false
            val aftermath = RemoteCommitAftermath.of(ok)
            teardown.execute {
                ack(aftermath.acknowledge) // 回执先行：手机端据此清草稿 / 提示失败
                if (aftermath.clearRemoteBuffer) {
                    // 上屏成功（决策 59）：TV 端立即清远程缓冲回等待输入态（大字区随 UI 通知清空，
                    // 远程模式框架与状态行不动）；缓冲清空与 UI 通知回主线程派发（主线程契约）
                    remoteBuffer.clear()
                    mainHandler.post { onRemoteTextChanged?.invoke(null) }
                }
                if (aftermath.stopService) {
                    stop() // 决策 59 后两分支恒 false；保留分支使停服决策收口于处置模型
                }
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
            Timber.tag(TAG).w("H5 页读取失败（${e.javaClass.simpleName}），回退占位页")
            PlaceholderPage.HTML.toByteArray(java.nio.charset.StandardCharsets.UTF_8)
        }
    }

    // ---- T25 · 远程空闲自动退出（第 8 章电量与资源 P1） ----

    /**
     * 空闲判定模型（单一事实源）：空闲口径按 PRD 括注 = 无手机连接；正式阈值 30 分钟
     * （RemoteIdlePolicy.DEFAULT_IDLE_TIMEOUT_MS，验证允许临时缩短、验后恢复）。
     * 状态变更仅发生在主线程（见 armIdleExit / cancelIdleExit 的 post 收口），无锁。
     */
    private val idlePolicy = RemoteIdlePolicy()

    /**
     * 在途空闲退出任务（仅主线程读写；null = 无排程）。到期回调经身份比对作废
     * （同 T22 连删 job 模式）：新窗口排程 / 解除都会替换或清空本引用。
     */
    private var idleJob: Runnable? = null

    /**
     * 进入无手机连接态（T25）：服务启动成功（进入远程模式）或已配对连接断开（回等待扫码）时
     * 调用，空闲自 [idleSinceMs]（调用方时刻，事件时刻而非 post 执行时刻）起算，并排程到期检查。
     * 任意线程可调用，统一 post 到主线程；服务已停的迟到调用不排程（stopNow 与本排程并发防御）。
     */
    private fun armIdleExit(idleSinceMs: Long) {
        mainHandler.post {
            if (!isRunning()) return@post
            idlePolicy.onIdleStarted(idleSinceMs)
            idleJob?.let { mainHandler.removeCallbacks(it) }
            val job = object : Runnable {
                override fun run() {
                    onIdleDeadlineReached(this)
                }
            }
            idleJob = job
            mainHandler.postDelayed(job, idlePolicy.idleTimeoutMs)
        }
    }

    /** 退出空闲跟踪（T25）：手机已连接（onPaired）或远程模式结束（stopNow）时调用；幂等。 */
    private fun cancelIdleExit() {
        mainHandler.post {
            idlePolicy.onIdleEnded()
            idleJob?.let { mainHandler.removeCallbacks(it) }
            idleJob = null
        }
    }

    /**
     * 空闲到期检查（T25，主线程）：任务身份比对不在途 → 忽略；空闲窗口判定未满阈值 → 忽略
     * （窗口状态与排程不一致的防御）；复核运行态与已配对会话（连接与到期同刻竞争时不得误退，
     * 空闲口径 = 无手机连接）→ 命中则走 [stop] 既有完整清理链路，并经主线程通知 IME 收起键盘。
     */
    private fun onIdleDeadlineReached(job: Runnable) {
        if (idleJob !== job) return
        idleJob = null
        if (!idlePolicy.shouldExit(System.currentTimeMillis())) return
        val paired = synchronized(this) { pairedSessions.hasPairedSession }
        if (!isRunning() || paired) return
        Timber.tag(TAG).i("远程模式空闲超时（无手机连接），自动退出并收起键盘")
        stop()
        mainHandler.post { onRemoteIdleExpired?.invoke() }
    }

    // ---- T70 · 已连接无活动自动退出（决策 59 · 第 8 章电量与资源行） ----

    /**
     * 已连接无活动判定模型（单一事实源）：正式阈值 10 分钟
     * （RemoteConnectedIdlePolicy.DEFAULT_CONNECTED_IDLE_TIMEOUT_MS，验证允许临时缩短、验后恢复；
     * debug 验证通道 debugConnectedIdleExpire 走活动回拨，不改动正式阈值）。
     * 状态变更仅发生在主线程（见 markRemoteActivity / cancelConnectedIdleWatch 的 post 收口），无锁。
     */
    private val connectedIdlePolicy = RemoteConnectedIdlePolicy()

    /** 在途「已连接无活动」到期任务（仅主线程读写；null = 无排程）。到期经身份比对作废（T22/T25 模式）。 */
    private var connectedIdleJob: Runnable? = null

    /**
     * 登记一次已配对会话上的 WS 消息收发（T70，任意线程可调，统一 post 到主线程串行化）：
     * 活动时刻记入 [connectedIdlePolicy]，窗口重新起算并重排到期任务（排满阈值）。
     * 服务已停 / 无已配对会话（断连竞态）不排程——前者无跟踪意义，后者回退交 T25 无连接口径。
     */
    private fun markRemoteActivity() {
        mainHandler.post {
            if (!isRunning()) return@post
            connectedIdlePolicy.onActivity(System.currentTimeMillis())
            if (!pairedSessions.hasPairedSession) return@post
            scheduleConnectedIdleCheck(connectedIdlePolicy.connectedIdleTimeoutMs)
        }
    }

    /** 重排「已连接无活动」到期任务（仅主线程调用）：旧任务身份作废，新任务延迟 [delayMs] 执行。 */
    private fun scheduleConnectedIdleCheck(delayMs: Long) {
        connectedIdleJob?.let { mainHandler.removeCallbacks(it) }
        val job = object : Runnable {
            override fun run() {
                onConnectedIdleDeadlineReached(this)
            }
        }
        connectedIdleJob = job
        mainHandler.postDelayed(job, delayMs)
    }

    /** 解除「已连接无活动」跟踪（T70）：会话断开 / 远程模式结束时调用；幂等，任意线程可调。 */
    private fun cancelConnectedIdleWatch() {
        mainHandler.post {
            connectedIdlePolicy.onSessionEnded()
            connectedIdleJob?.let { mainHandler.removeCallbacks(it) }
            connectedIdleJob = null
        }
    }

    /**
     * 已连接无活动到期检查（T70，主线程）：任务身份比对不在途 → 忽略；窗口判定未满阈值 → 忽略
     * （窗口状态与排程不一致的防御）；复核运行态与已配对会话（断连与到期同刻竞争时不得误退，
     * 断连清理与回退由断连链路 / T25 口径负责）→ 命中则走 [stop] 既有完整清理链路
     *（先向手机回发 disconnect { reason:"tv-exit" } 再按 1000 关连接后停服，FR-B7 通知链），
     * 并经主线程通知 IME 收起键盘。
     */
    private fun onConnectedIdleDeadlineReached(job: Runnable) {
        if (connectedIdleJob !== job) return
        connectedIdleJob = null
        if (!connectedIdlePolicy.shouldExit(System.currentTimeMillis())) return
        val paired = synchronized(this) { pairedSessions.hasPairedSession }
        if (!isRunning() || !paired) return
        Timber.tag(TAG).i("已连接无活动超时（决策 59），自动退出并收起键盘")
        stop()
        mainHandler.post { onRemoteIdleExpired?.invoke() }
    }

    /**
     * 已连接无活动超时的 debug 验证通道（T70 · 决策 21 口径，仅 debug 构建可达）：把活动时刻
     * 回拨一个完整窗口并立即排程到期——走与真实到期完全相同的身份比对 / 窗口判定 / 运行态与
     * 已配对会话复核 / [stop] 清理链（通知手机 tv-exit + 1000 关连接 + 停服 + 收起键盘），
     * 正式 10 分钟阈值不改动；验后无需恢复。无已配对会话时忽略（该态归 T25 无连接口径）。
     */
    fun debugConnectedIdleExpire() {
        if (!BuildConfig.DEBUG) {
            Timber.tag(TAG).w("已连接无活动模拟入口仅 debug 构建可用（决策 21）")
            return
        }
        mainHandler.post {
            if (!isRunning() || !pairedSessions.hasPairedSession) {
                Timber.tag(TAG).i("已连接无活动模拟忽略：无已配对会话")
                return@post
            }
            connectedIdlePolicy.onActivity(System.currentTimeMillis() - connectedIdlePolicy.connectedIdleTimeoutMs)
            scheduleConnectedIdleCheck(0L)
        }
    }

    // ---- T18 · 网络变化自动重建（Q2 lead 拍板 A · PRD 5.3「二维码 IP 失效时自动重建服务并刷新二维码」） ----

    /**
     * 注册默认网络变化回调（服务运行期有效，stopNow 解注册）：监听能力网络——
     * 丢失（onLost）置「待重建」标记；恢复（onAvailable）或链路属性变化（onLinkPropertiesChanged，
     * 含 IP 换段）时比对当前默认路由 IPv4 与绑定地址，按需重建。注册失败静默降级（无自动重建，
     * 不影响服务本体，可靠性口径）。
     */
    private fun registerNetworkWatcher(appContext: Context) {
        if (networkCallback != null) return
        val connectivity = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onLost(network: Network) {
                synchronized(this@RemoteInputServerManager) {
                    if (server != null && !stopping) rebuildAfterNetworkLoss = true
                }
            }

            override fun onAvailable(network: Network) {
                handleNetworkMaybeChanged()
            }

            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                handleNetworkMaybeChanged()
            }
        }
        val request = android.net.NetworkRequest.Builder()
            .addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        try {
            connectivity.registerNetworkCallback(request, callback)
            networkCallback = callback
            Timber.tag(TAG).i("网络变化监听已注册（运行期生效，停止即解注册）")
        } catch (_: Exception) {
            networkCallback = null
            Timber.tag(TAG).w("网络变化监听注册失败（降级为无自动重建）")
        }
    }

    /** 解注册网络回调（stopNow 调用；幂等）。 */
    private fun unregisterNetworkWatcher() {
        val callback = networkCallback ?: return
        networkCallback = null
        val context = watcherContext ?: return
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        runCatching { connectivity.unregisterNetworkCallback(callback) }
    }

    /**
     * 网络事件统一判定（回调线程进入）：两条重建触发规则——
     * ① 「运行中默认网络丢失」标记已置位（onLost 置位，恢复后的首个事件消费）→ 重建
     *   （同网段瞬断亦重建：恢复事件即视为服务可达性已破坏一次，换新端口刷新二维码，如实口径）；
     * ② 当前默认路由 IPv4 与绑定地址不一致（onLinkPropertiesChanged / onAvailable 携带换段）→ 重建。
     * 两规则均不命中 → 服务保持（无网络事件语义上的破坏）。
     * 判定在 synchronized 区内完成，重建编排移出锁外。
     */
    private fun handleNetworkMaybeChanged() {
        val context = watcherContext ?: return
        val shouldRebuild = synchronized(this) {
            if (server == null || stopping || rebuilding) return
            val lost = rebuildAfterNetworkLoss
            rebuildAfterNetworkLoss = false
            val currentAddress = resolveLanAddress(context)?.hostAddress
            lost || (currentAddress != null && currentAddress != addressText)
        }
        if (shouldRebuild) rebuildForNetworkChange()
    }

    /**
     * 重建编排（teardown 线程执行）：尽力向已配对手机回发 disconnect 并关连接（对端多半已
     * 不可达，写失败静默）→ 停服 → 重建启动（新随机端口 + 新 token，二维码随 UI 刷新）。
     * 重建决策后用户已显式退出（exitRequested）则不复活服务。防重入由 [rebuilding] 保证。
     */
    private fun rebuildForNetworkChange() {
        val context = watcherContext ?: return
        synchronized(this) {
            if (server == null || stopping || rebuilding) return
            rebuilding = true
        }
        val notifyTarget: PairedConnection? = synchronized(this) { pairedSessions.currentConnection() }
        Timber.tag(TAG).i("检测到网络变化，重建远程输入服务并刷新二维码")
        teardown.execute {
            // rebuilding 收口保证（R2 · 建议 2 采纳）：任务体任意异常不悬挂防重入标记
            runCatching {
                // 尽力通知（R2：reason=network-rebuild，手机端呈现 FR-C8 重连引导而非「电视已退出」；
                // 重建场景对端多半不可达，写失败静默）
                if (notifyTarget != null) {
                    runCatching { notifyTarget.sender.send(DISCONNECT_NETWORK_REBUILD_MESSAGE) }
                    runCatching { notifyTarget.close(CLOSE_NORMAL_EXIT) }
                }
                stopNow()
                val restart = synchronized(this) { !exitRequested }
                if (restart) {
                    when (val result = startInternal(context)) {
                        is RemoteInputServer.StartResult.Started ->
                            Timber.tag(TAG).i("网络变化重建完成：${addressUrl()}；配对地址：$pairingUrlText（二维码已刷新）")
                        is RemoteInputServer.StartResult.Failed ->
                            Timber.tag(TAG).w("网络变化重建失败：${result.reason}")
                    }
                } else {
                    Timber.tag(TAG).i("网络变化重建取消：远程输入已被退出")
                }
            }
            synchronized(this) { rebuilding = false }
        }
    }

    /**
     * debug 走查通道（T18 · 决策 21 口径，仅 debug 构建可达）：模拟「运行中默认网络丢失后恢复」——
     * 置丢失标记后进入与真实网络回调完全相同的 [handleNetworkMaybeChanged] 判定与重建路径。
     * 依据：模拟器镜像默认网络为 eth0（ETHERNET），`cmd connectivity airplane-mode` 不注销该网络、
     * `svc wifi` 通断不产生网络登记（实测），shell 无 root / ip 受限，回调接线在模拟器不可触发；
     * 真机 Wi-Fi 开关的真实触发随 T19 里程碑验证。重建后新随机端口 + 新 token（二维码刷新）。
     */
    fun debugNetworkChange() {
        // R2（评审建议 4 采纳）：BuildConfig.DEBUG 兜底对齐决策 21 防御纵深（与 debug 接收器双重门控）
        if (!BuildConfig.DEBUG) {
            Timber.tag(TAG).w("网络变化模拟入口仅 debug 构建可用（决策 21）")
            return
        }
        synchronized(this) {
            if (server == null || stopping) {
                Timber.tag(TAG).i("网络变化模拟忽略：服务未运行")
                return
            }
            rebuildAfterNetworkLoss = true
        }
        handleNetworkMaybeChanged()
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

/**
 * 服务端口持久化（T75 · 决策 63）：SharedPreferences 适配（T71 DebugLogStore 先例口径）——
 * 仅持久化「端口号」这一个配置项，无用户输入内容与身份信息（隐私红线不破）；
 * 跨停服 / 跨进程重启保留。经 [RemotePortPersistence] 接口供端口策略与 JVM 单测注入。
 */
private class RemoteServicePortStore(context: Context) : RemotePortPersistence {

    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 读持久化端口（决策 63 首轮启动无记录返回 null，交由策略走随机分配）。 */
    override fun read(): Int? = prefs.getInt(KEY_SERVICE_PORT, -1).takeIf { it > 0 }

    /** 持久化端口（仅策略在真实绑定成功后调用）。 */
    override fun write(port: Int) {
        prefs.edit().putInt(KEY_SERVICE_PORT, port).apply()
    }

    private companion object {
        const val PREFS_NAME = "remote_service_prefs"
        const val KEY_SERVICE_PORT = "service_port"
    }
}

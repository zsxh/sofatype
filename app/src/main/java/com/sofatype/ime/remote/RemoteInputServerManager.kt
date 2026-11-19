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

    private const val MAIN_PAGE_ASSET = "remote/index.html"

    @Volatile
    private var server: RemoteInputServer? = null

    @Volatile
    private var addressText: String? = null

    private val tokens = PairingTokenStore()

    private val pairedSessions = PairedSessionRegistry()

    private val sessionControls = HashMap<PairingSession, PairedConnection>()

    private const val REPLACED_MESSAGE = "{\"type\":\"${PairingSession.MSG_REPLACED}\"}"

    private const val DISCONNECT_TV_EXIT_MESSAGE =
        "{\"type\":\"${PairingSession.MSG_DISCONNECT}\",\"reason\":\"${PairingSession.REASON_TV_EXIT}\"}"

    private const val DISCONNECT_NETWORK_REBUILD_MESSAGE =
        "{\"type\":\"${PairingSession.MSG_DISCONNECT}\",\"reason\":\"${PairingSession.REASON_NETWORK_REBUILD}\"}"

    private const val CLOSE_NORMAL_EXIT = 1000

    private const val DISCONNECT_NOTICE_MS = 3_000L

    private val teardown = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "sofatype-remote-teardown").apply { isDaemon = true }
    }

    @Volatile
    private var stopping = false

    @Volatile
    private var exitRequested = false

    @Volatile
    private var rebuilding = false

    @Volatile
    private var disconnectNoticeUntil = 0L

    @Volatile
    private var rebuildAfterNetworkLoss = false

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    @Volatile
    private var watcherContext: Context? = null

    @Volatile
    private var pairedDeviceName: String? = null

    @Volatile
    private var pairedDeviceKey: String? = null

    @Volatile
    private var pairedClientCode: ClientCode? = null

    private val clientCodes = ClientCodeRegistry()

    @Volatile
    private var pairingUrlText: String? = null

    private val remoteBuffer = RemoteTextBuffer(PairingSession.DEFAULT_CHAR_LIMIT)

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var mainPageCache: ByteArray? = null

    @Volatile
    var onPairingStateChanged: (() -> Unit)? = null

    @Volatile
    var onRemoteTextChanged: ((String?) -> Unit)? = null

    @Volatile
    var onCommitRequested: ((String) -> Boolean)? = null

    @Volatile
    var onRemoteIdleExpired: (() -> Unit)? = null

    fun isRunning(): Boolean = server?.isRunning == true

    fun addressUrl(): String? {
        val addr = addressText
        val port = server?.port
        return if (addr != null && port != null) "http://$addr:$port/" else null
    }

    fun pairingUrl(): String? = pairingUrlText

    fun isPairingTokenActive(): Boolean = tokens.isActive

    fun hasPairedSession(): Boolean = pairedSessions.hasPairedSession

    fun pairedDeviceName(): String? = pairedDeviceName

    fun pairedClientCode(): ClientCode? = pairedClientCode

    fun remoteTextSnapshot(): String? = remoteBuffer.text.takeIf { it.isNotEmpty() }

    fun disconnectNoticeActive(): Boolean = disconnectNoticeUntil > System.currentTimeMillis()

    fun start(context: Context): RemoteInputServer.StartResult {
        synchronized(this) {
            exitRequested = false
        }
        return startInternal(context)
    }

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
            tokens.issue()
            pairedDeviceName = null
            pairedDeviceKey = null
            pairedClientCode = null
            clientCodes.unpin()
            disconnectNoticeUntil = 0
            exitRequested = false
            mainPageCache = null
            val tvName = resolveTvName()

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
                            PortBindAttempt.Occupied
                        } else {
                            PortBindAttempt.Unavailable(result.reason)
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
                else -> Unit
            }
            server = instance
            addressText = address.hostAddress
            pairingUrlText = "http://${address.hostAddress}:${startedPort}/?token=${tokens.peek()}"
            watcherContext = context.applicationContext
            registerNetworkWatcher(context.applicationContext)

            armIdleExit(System.currentTimeMillis())

            Timber.tag(TAG).i("远程输入服务已启动：${addressUrl()}；配对地址：${pairingUrlText}（WS 端点同端口 /ws）")

            mainHandler.post { onPairingStateChanged?.invoke() }
            return RemoteInputServer.StartResult.Started(startedPort)
        }
    }

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

    fun stop(): Boolean {
        val notifyTarget: PairedConnection?
        synchronized(this) {

            exitRequested = true
            if (server == null || stopping) return false
            stopping = true
            notifyTarget = pairedSessions.currentConnection()
        }
        if (notifyTarget == null) {
            stopNow()
            return true
        }
        teardown.execute {

            runCatching { notifyTarget.sender.send(DISCONNECT_TV_EXIT_MESSAGE) }
            runCatching { notifyTarget.close(CLOSE_NORMAL_EXIT) }
            stopNow()
        }
        return true
    }

    private fun stopNow() {
        synchronized(this) {
            stopping = false

            rebuildAfterNetworkLoss = false
            disconnectNoticeUntil = 0
            unregisterNetworkWatcher()
            watcherContext = null
            val instance = server ?: return
            server = null
            addressText = null
            pairingUrlText = null
            tokens.clear()
            pairedSessions.clear()
            sessionControls.clear()
            pairedDeviceName = null
            pairedDeviceKey = null
            pairedClientCode = null
            clientCodes.unpin()
            cancelIdleExit()
            cancelConnectedIdleWatch()
            instance.stop()
            Timber.tag(TAG).i("远程输入服务已停止，监听已关闭，配对 token 已作废")

            remoteBuffer.clear()
            mainHandler.post { onRemoteTextChanged?.invoke(null) }
            mainHandler.post { onPairingStateChanged?.invoke() }
        }
    }

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
                val token = tokens.issue()
                pairingUrlText = "http://$addr:$port/?token=$token"
                Timber.tag(TAG).i("配对 token 已滚动签发，二维码内容更新")
            }

            pairedClientCode = clientCodes.issue(deviceKey, deviceType, System.currentTimeMillis())
            pairedDeviceKey = deviceKey
            clientCodes.pin(deviceKey)
            pairedDeviceName = deviceName
            cancelIdleExit()
            markRemoteActivity()
        }
        mainHandler.post { onPairingStateChanged?.invoke() }
    }

    private fun onPairedConnectionAttached(
        session: PairingSession,
        sender: WsMessageSender,
        close: (Int) -> Unit,
    ) {
        synchronized(this) {
            sessionControls[session] = PairedConnection(sender, close)
        }
    }

    private fun onPairedSessionClosed(session: PairingSession) {
        val cleared = synchronized(this) {
            sessionControls.remove(session)
            pairedSessions.detachIfCurrent(session)
        }
        if (cleared) {
            pairedDeviceName = null
            pairedDeviceKey = null
            pairedClientCode = null
            clientCodes.unpin()
            remoteBuffer.clear()
            Timber.tag(TAG).i("已配对连接断开，回等待扫码态（配对码仍有效）")

            armIdleExit(System.currentTimeMillis())

            cancelConnectedIdleWatch()
            mainHandler.post {

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

    private fun onRemoteInput(text: String) {
        markRemoteActivity()
        clientCodes.touch(pairedDeviceKey, System.currentTimeMillis())
        val stored = remoteBuffer.set(text)
        mainHandler.post { onRemoteTextChanged?.invoke(stored) }
    }

    private fun onRemoteClear() {
        markRemoteActivity()
        clientCodes.touch(pairedDeviceKey, System.currentTimeMillis())
        remoteBuffer.clear()
        mainHandler.post { onRemoteTextChanged?.invoke(null) }
    }

    private fun onRemoteCommit(text: String, ack: (Boolean) -> Unit) {
        markRemoteActivity()
        clientCodes.touch(pairedDeviceKey, System.currentTimeMillis())
        mainHandler.post {
            val ok = onCommitRequested?.invoke(text) ?: false
            val aftermath = RemoteCommitAftermath.of(ok)
            teardown.execute {
                ack(aftermath.acknowledge)
                if (aftermath.clearRemoteBuffer) {

                    remoteBuffer.clear()
                    mainHandler.post { onRemoteTextChanged?.invoke(null) }
                }
                if (aftermath.stopService) {
                    stop()
                }
            }
        }
    }

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

    private val idlePolicy = RemoteIdlePolicy()

    private var idleJob: Runnable? = null

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

    private fun cancelIdleExit() {
        mainHandler.post {
            idlePolicy.onIdleEnded()
            idleJob?.let { mainHandler.removeCallbacks(it) }
            idleJob = null
        }
    }

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

    private val connectedIdlePolicy = RemoteConnectedIdlePolicy()

    private var connectedIdleJob: Runnable? = null

    private fun markRemoteActivity() {
        mainHandler.post {
            if (!isRunning()) return@post
            connectedIdlePolicy.onActivity(System.currentTimeMillis())
            if (!pairedSessions.hasPairedSession) return@post
            scheduleConnectedIdleCheck(connectedIdlePolicy.connectedIdleTimeoutMs)
        }
    }

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

    private fun cancelConnectedIdleWatch() {
        mainHandler.post {
            connectedIdlePolicy.onSessionEnded()
            connectedIdleJob?.let { mainHandler.removeCallbacks(it) }
            connectedIdleJob = null
        }
    }

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

    private fun unregisterNetworkWatcher() {
        val callback = networkCallback ?: return
        networkCallback = null
        val context = watcherContext ?: return
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        runCatching { connectivity.unregisterNetworkCallback(callback) }
    }

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

    private fun rebuildForNetworkChange() {
        val context = watcherContext ?: return
        synchronized(this) {
            if (server == null || stopping || rebuilding) return
            rebuilding = true
        }
        val notifyTarget: PairedConnection? = synchronized(this) { pairedSessions.currentConnection() }
        Timber.tag(TAG).i("检测到网络变化，重建远程输入服务并刷新二维码")
        teardown.execute {

            runCatching {

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

    fun debugNetworkChange() {

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

    private fun resolveTvName(): String =
        Build.MODEL?.trim()?.takeIf { it.isNotEmpty() } ?: "Android TV"

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
                    null
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
        return BindAddressSelector.select(candidates)?.ipv4?.let { InetAddress.getByName(it) }
    }
}

private class RemoteServicePortStore(context: Context) : RemotePortPersistence {

    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun read(): Int? = prefs.getInt(KEY_SERVICE_PORT, -1).takeIf { it > 0 }

    override fun write(port: Int) {
        prefs.edit().putInt(KEY_SERVICE_PORT, port).apply()
    }

    private companion object {
        const val PREFS_NAME = "remote_service_prefs"
        const val KEY_SERVICE_PORT = "service_port"
    }
}

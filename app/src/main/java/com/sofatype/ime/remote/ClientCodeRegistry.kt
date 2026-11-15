// T72 · 远程输入连接代号 · TV 端序号注册表（决策 61）：LRU 内存表 deviceKey→(类型, 序号, lastSeen)。
// 签发与保号口径（决策 61 原文）：
//   命中沿用原序号（刷新 / 断线重连 / 被顶替后再连，窗口内代号不变）；
//   未命中取全表最大序号 +1（表空回 01）；
//   条目 TTL 15 分钟自最近一次 WS 活动滚动续期（已连接期间持续续期，连接中代号永不变，
//   代号刷新只发生在重连时刻）；容量上限 8 台、满淘汰最久未见；
//   hello 未携带 deviceKey（旧缓存页 / 非浏览器客户端）按匿名新设备签发、仅当次连接内固定。
// 与决策 59（T70 已连接无活动 10 分钟断连）衔接：断开后窗口自最后活动起算 15 分钟，
// 期内重连保号——断开不清表、过期惰性清除（下次 issue / touch 访问时清理）。
// 隐私红线（决策 34/60 延伸）：deviceKey 与序号仅本表内存驻留，不持久化、不入日志；
// 服务停止不清本表（保号窗口跨 TV 退出 / 服务重启持续，进程死亡才随内存消亡）。
// 「连接中代号永不变」的落点：当前已配对连接的 deviceKey 经 [pin] 固定，惰性清除与
// 满员淘汰一律跳过钉住键（钉住键 TTL 过期也不清，代号只随重连时刻刷新）。
// 纯 Kotlin，不依赖 Android 类；表内读写经 synchronized（WS 读线程 / 主线程多点触达），
// 时钟由调用方注入 nowMs（可单测锁定 TTL 语义，本类不自取系统时钟）。
package com.sofatype.ime.remote

/** 连接代号值对象（决策 61：[DeviceType.label]设备 + 两位序号，示例「微信设备 01」）。 */
data class ClientCode(
    /** 设备类型（hello.deviceType 判定结果，TV 不解析 UA）。 */
    val type: DeviceType,

    /** 两位序号（注册表签发）。 */
    val number: Int,
) {
    /** 序号展示文本（两位，不足补零；胶囊序号 span 与全文同源此口径）。 */
    val numberText: String
        get() = number.toString().padStart(MIN_NUMBER_WIDTH, '0')

    /** 胶囊展示全文（state.clientCode 与两端展示的唯一文本口径，序号不足两位补零）。 */
    val text: String
        get() = "${type.label}$DEVICE_SUFFIX $numberText"

    companion object {
        /** 序号展示位宽（决策 61：两位序号）。 */
        private const val MIN_NUMBER_WIDTH = 2

        /** 代号设备类型后缀（决策 61 胶囊文案：「微信设备 01」的「设备」）。 */
        const val DEVICE_SUFFIX = "设备"
    }
}

class ClientCodeRegistry(
    /** 容量上限（决策 61：8 台，满淘汰最久未见）。 */
    private val capacity: Int = DEFAULT_CAPACITY,

    /** 条目 TTL（决策 61：15 分钟，自最近一次 WS 活动滚动续期）。 */
    private val ttlMs: Long = DEFAULT_TTL_MS,
) {

    /** 表内条目：类型 + 序号 + 最近一次 WS 活动时刻（决策 61 三元组）。 */
    private class Entry(
        var type: DeviceType,
        val number: Int,
        var lastSeenMs: Long,
    )

    /** deviceKey → 条目（LRU 语义以 lastSeen 表达，无独立访问序结构）。 */
    private val entries = HashMap<String, Entry>()

    /** 当前已配对连接的 deviceKey（连接中代号永不变：清除与淘汰一律跳过）。 */
    @Volatile
    private var pinnedKey: String? = null

    /**
     * 签发 / 沿用连接代号（配对成功时刻调用；决策 61 未命中签发规则）：
     * - [deviceKey] 命中：沿用原序号（代号不变），类型随本次 hello 更新，lastSeen 续期；
     * - [deviceKey] 未命中：全表最大序号 +1（表空回 01）后入表；满员先淘汰最久未见（跳过钉住键）；
     * - [deviceKey] 为 null（匿名）：按匿名新设备签发同规则序号，不入表（仅当次连接内固定）。
     */
    fun issue(deviceKey: String?, type: DeviceType, nowMs: Long): ClientCode = synchronized(this) {
        purgeExpired(nowMs)
        if (deviceKey == null) {
            return ClientCode(type, nextNumber())
        }
        val hit = entries[deviceKey]
        if (hit != null) {
            hit.type = type
            hit.lastSeenMs = nowMs
            return ClientCode(type, hit.number)
        }
        if (entries.size >= capacity) evictOldestUnpinned()
        val number = nextNumber()
        entries[deviceKey] = Entry(type, number, nowMs)
        ClientCode(type, number)
    }

    /**
     * WS 活动滚动续期（决策 61：自最近一次 WS 活动滚动续期；input / clear / commit 到达时调用）。
     * 匿名连接（null）与已过期清除的键为无害 no-op。
     */
    fun touch(deviceKey: String?, nowMs: Long) {
        if (deviceKey == null) return
        synchronized(this) {
            entries[deviceKey]?.lastSeenMs = nowMs
        }
    }

    /**
     * 钉住 / 解除当前已配对连接的 deviceKey（连接中代号永不变）：
     * [pin] 在配对成功（issue）后调用；[unpin] 在会话断开 / 服务停止时调用（幂等）。
     * 钉住键不受惰性过期清除与满员淘汰影响；匿名连接钉住 null = 无键可保（匿名本就不入表）。
     */
    fun pin(deviceKey: String?) {
        pinnedKey = deviceKey
    }

    /** 解除钉住（会话断开 / 服务停止；幂等）。 */
    fun unpin() {
        pinnedKey = null
    }

    /** 清表（仅测试用：生产口径不停服不清表——保号窗口跨停服持续，见类注释）。 */
    fun clear() = synchronized(this) {
        entries.clear()
    }

    /** 惰性过期清除（决策 61：断开后 TTL 满即失号；钉住键跳过）。 */
    private fun purgeExpired(nowMs: Long) {
        val expiredBefore = nowMs - ttlMs
        val iterator = entries.entries.iterator()
        while (iterator.hasNext()) {
            val item = iterator.next()
            val pinned = item.key == pinnedKey
            if (!pinned && item.value.lastSeenMs <= expiredBefore) iterator.remove()
        }
    }

    /** 满员淘汰最久未见（跳过钉住键；表满且仅剩钉住键时不淘汰——防御分支，容量 ≥1 恒可达）。 */
    private fun evictOldestUnpinned() {
        val oldest = entries.entries
            .filter { it.key != pinnedKey }
            .minByOrNull { it.value.lastSeenMs } ?: return
        entries.remove(oldest.key)
    }

    /** 全表最大序号 +1（表空回 01；匿名签发同口径）。 */
    private fun nextNumber(): Int = (entries.values.maxOfOrNull { it.number } ?: 0) + 1

    companion object {
        /** 容量上限（决策 61：8 台）。 */
        const val DEFAULT_CAPACITY = 8

        /** 条目 TTL（决策 61：15 分钟，滚动续期）。 */
        const val DEFAULT_TTL_MS = 15 * 60 * 1000L
    }
}

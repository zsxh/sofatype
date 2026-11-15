// T75 · 决策 63 · 远程服务端口分配策略（首次随机、持久化、使用期复用）。
// 纯 Kotlin（不依赖 android.*），可被 JVM 单元测试覆盖；Android 持久化适配
// （SharedPreferences，仅端口号一个配置项，T71 先例口径）见 RemoteInputServerManager。
// 口径（决策 63，修订决策 16 端口随机条款）：
// - 每轮启动优先复用上次持久化端口；复用命中即沿用（持久化值不变）；
// - 复用被占（BindException）或无记录 → IANA 动态段 49152–65535 随机取端口绑定，
//   仍被占则重新随机，有限重试（RANDOM_RETRY_LIMIT = 3 次）仍失败按启动失败明示原因，
//   不静默放弃；
// - 随机绑定成功即持久化该端口号（跨停服 / 跨进程重启保留）；
// - 无记录 / 持久化值越界（不在动态段）按无持久化端口处理，直接进入随机分配。
package com.sofatype.ime.remote

import java.util.concurrent.ThreadLocalRandom

/**
 * 端口持久化面（T75 · 决策 63）：生产实现走 SharedPreferences（T71 先例口径），
 * 单元测试注入内存实现。只承载端口号一个配置项，无用户输入内容与身份信息（隐私红线不破）。
 */
interface RemotePortPersistence {
    /** 读上次持久化的端口号；无记录返回 null。 */
    fun read(): Int?

    /** 持久化端口号（仅真实绑定成功后调用）。 */
    fun write(port: Int)
}

/**
 * 单次端口绑定尝试的结论（T75）：由调用方对真实绑定结果分类后回填给策略——
 * 策略本身不触碰网络，只做候选端口决策。
 */
sealed class PortBindAttempt {
    /** 绑定成功，[port] 即实际监听端口。 */
    data class Bound(val port: Int) : PortBindAttempt()

    /** 端口被占（BindException）：按决策 63 换新随机端口重试。 */
    object Occupied : PortBindAttempt()

    /** 非端口占用的启动失败：终止策略并原样上抛原因，不做端口重试（不静默）。 */
    data class Unavailable(val reason: String) : PortBindAttempt()
}

/** 端口分配最终结论（T75 · 决策 63）。 */
sealed class PortAssignment {
    /** 复用上次持久化端口命中（持久化值不变，不回写）。 */
    data class Reused(val port: Int) : PortAssignment()

    /**
     * 随机分配命中（首次启动无记录，或上次端口被占换新）；新端口已持久化。
     * [previousPort] = 被换掉的上一轮持久化端口（首次启动无记录为 null），供日志与验证。
     */
    data class Randomized(val port: Int, val previousPort: Int?) : PortAssignment()

    /** 端口分配最终失败（随机重试上限内均被占，或非占用类启动失败），[reason] 可直接记录。 */
    data class Failed(val reason: String) : PortAssignment()
}

/**
 * 远程服务端口分配策略（T75 · 决策 63 单一事实源）：按上方口径产出候选端口序列并汇总结论。
 * [bind] 由调用方实现真实 ServerSocket 绑定并回填 [PortBindAttempt]；绑定成功实例的
 * 保留 / 运行态接线由调用方负责（本策略每次绑定失败即放弃该候选实例）。
 */
class RemotePortPolicy(
    private val persistence: RemotePortPersistence,
    /** 闭区间 [min, max] 内取随机整数；生产默认 ThreadLocalRandom，测试注入确定性序列。 */
    private val randomInRange: (min: Int, max: Int) -> Int =
        { min, max -> ThreadLocalRandom.current().nextInt(min, max + 1) },
) {

    /**
     * 决策 63 端口分配主流程：
     * ① 读上次持久化端口（越界 / 非法按无记录处理）→ 绑定成功即复用命中；
     * ② 复用被占或无记录 → 动态段随机取端口绑定，被占即重新随机，上限 [RANDOM_RETRY_LIMIT] 次；
     * ③ 随机绑定成功 → 持久化新端口号并返回结论；
     * ④ 非占用类失败原样终止（不重试）；上限内均被占 → 明示失败原因（不静默）。
     */
    fun resolve(bind: (Int) -> PortBindAttempt): PortAssignment {
        val persisted = persistence.read()?.takeIf { it in MIN_PORT..MAX_PORT }
        if (persisted != null) {
            when (val attempt = bind(persisted)) {
                is PortBindAttempt.Bound -> return PortAssignment.Reused(attempt.port)
                is PortBindAttempt.Unavailable -> return PortAssignment.Failed(attempt.reason)
                PortBindAttempt.Occupied -> Unit // 落入下方随机重试
            }
        }
        repeat(RANDOM_RETRY_LIMIT) {
            val candidate = randomInRange(MIN_PORT, MAX_PORT)
            when (val attempt = bind(candidate)) {
                is PortBindAttempt.Bound -> {
                    persistence.write(attempt.port)
                    return PortAssignment.Randomized(attempt.port, persisted)
                }
                is PortBindAttempt.Unavailable -> return PortAssignment.Failed(attempt.reason)
                PortBindAttempt.Occupied -> Unit
            }
        }
        val previous = persisted?.let { "上次持久化端口 $it 被占，" } ?: ""
        return PortAssignment.Failed(
            "端口分配失败：${previous}动态段 $MIN_PORT–$MAX_PORT 随机重试 $RANDOM_RETRY_LIMIT 次均被占用（决策 63）",
        )
    }

    companion object {
        /** IANA 动态端口段下界（决策 63）。 */
        const val MIN_PORT = 49152

        /** IANA 动态端口段上界（含，决策 63）。 */
        const val MAX_PORT = 65535

        /** 持久化端口被占（或无记录）后动态段随机重试次数上限（决策 63：≥3 次）。 */
        const val RANDOM_RETRY_LIMIT = 3
    }
}

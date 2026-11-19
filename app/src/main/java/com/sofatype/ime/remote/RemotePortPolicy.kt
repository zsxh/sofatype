package com.sofatype.ime.remote

import java.util.concurrent.ThreadLocalRandom

interface RemotePortPersistence {

    fun read(): Int?

    fun write(port: Int)
}

sealed class PortBindAttempt {

    data class Bound(val port: Int) : PortBindAttempt()

    object Occupied : PortBindAttempt()

    data class Unavailable(val reason: String) : PortBindAttempt()
}

sealed class PortAssignment {

    data class Reused(val port: Int) : PortAssignment()

    data class Randomized(val port: Int, val previousPort: Int?) : PortAssignment()

    data class Failed(val reason: String) : PortAssignment()
}

class RemotePortPolicy(
    private val persistence: RemotePortPersistence,

    private val randomInRange: (min: Int, max: Int) -> Int =
        { min, max -> ThreadLocalRandom.current().nextInt(min, max + 1) },
) {

    fun resolve(bind: (Int) -> PortBindAttempt): PortAssignment {
        val persisted = persistence.read()?.takeIf { it in MIN_PORT..MAX_PORT }
        if (persisted != null) {
            when (val attempt = bind(persisted)) {
                is PortBindAttempt.Bound -> return PortAssignment.Reused(attempt.port)
                is PortBindAttempt.Unavailable -> return PortAssignment.Failed(attempt.reason)
                PortBindAttempt.Occupied -> Unit
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

        const val MIN_PORT = 49152

        const val MAX_PORT = 65535

        const val RANDOM_RETRY_LIMIT = 3
    }
}

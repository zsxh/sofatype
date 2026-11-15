// T75 · 决策 63 · 端口分配策略单元测试（纯 JVM，不触网络）：复用命中 / 被占换新 /
// 持久化回读 / 重试上限 / 非占用失败即停 / 越界持久化值按无记录处理 / 动态段边界。
// 绑定探针注入确定性结论与候选序列，覆盖 RemotePortPolicy 全部分支。
package com.sofatype.ime.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemotePortPolicyTest {

    /** 内存持久化（测试注入）：行为对齐生产 SharedPreferences 语义（写后跨实例可读）。 */
    private class InMemoryPortStore(initial: Int? = null) : RemotePortPersistence {
        var value: Int? = initial
        var writeCount = 0
        override fun read(): Int? = value
        override fun write(port: Int) {
            value = port
            writeCount++
        }
    }

    /** 绑定探针：记录全部候选端口；落在 [occupied] 内回 Occupied，其余回 Bound。 */
    private class Probe(private val occupied: Set<Int> = emptySet()) {
        val candidates = mutableListOf<Int>()

        fun bind(port: Int): PortBindAttempt {
            candidates.add(port)
            return if (port in occupied) PortBindAttempt.Occupied else PortBindAttempt.Bound(port)
        }
    }

    // ---- 复用命中 ----

    @Test
    fun reusesPersistedPortWhenBindable() {
        val store = InMemoryPortStore(51000)
        val probe = Probe()
        val assignment =
            RemotePortPolicy(store) { _, _ -> throw AssertionError("复用命中不应触发随机分配") }
                .resolve(probe::bind)
        assertEquals(PortAssignment.Reused(51000), assignment)
        assertEquals("只试持久化端口，无随机候选", listOf(51000), probe.candidates)
        assertEquals("复用命中值不变，不回写持久化", 0, store.writeCount)
    }

    // ---- 持久化回读 ----

    @Test
    fun persistedPortIsReadBackAcrossPolicyInstances() {
        // 首轮（无记录）：随机分配命中并持久化
        val store = InMemoryPortStore()
        val first = RemotePortPolicy(store) { _, _ -> 55555 }.resolve { p -> PortBindAttempt.Bound(p) }
        assertEquals(PortAssignment.Randomized(55555, null), first)
        assertEquals(55555, store.value)
        assertEquals(1, store.writeCount)
        // 次轮（新策略实例 = 模拟跨停服 / 跨进程重启后）：仅凭持久化值复用命中，随机通道未触碰
        var randomCalled = false
        val second =
            RemotePortPolicy(store) { _, _ -> randomCalled = true; 60000 }
                .resolve { p -> PortBindAttempt.Bound(p) }
        assertEquals(PortAssignment.Reused(55555), second)
        assertTrue("次轮不应进入随机分配", !randomCalled)
    }

    // ---- 被占换新 ----

    @Test
    fun occupiedPersistedPortYieldsNewRandomPersistedPort() {
        val store = InMemoryPortStore(50000)
        val probe = Probe(occupied = setOf(50000))
        val sequence = listOf(52000, 53000, 54000)
        var next = 0
        val assignment =
            RemotePortPolicy(store) { _, _ -> sequence[next++] }.resolve(probe::bind)
        assertEquals(PortAssignment.Randomized(52000, 50000), assignment)
        assertEquals("持久化端口被占后换新随机一次即命中", listOf(50000, 52000), probe.candidates)
        assertEquals("新端口已持久化", 52000, store.value)
        assertEquals(1, store.writeCount)
    }

    @Test
    fun occupiedPersistedPortThenRandomRetriesHitWithinLimit() {
        // 复用被占 + 前两次随机被占 + 第三次（上限内末次）随机命中
        val store = InMemoryPortStore(49200)
        var idx = 0
        val seen = mutableListOf<Int>()
        val assignment =
            RemotePortPolicy(store) { _, _ -> listOf(51000, 52000, 53000)[idx++] }.resolve { port ->
                seen.add(port)
                if (port == 53000) PortBindAttempt.Bound(port) else PortBindAttempt.Occupied
            }
        assertEquals(PortAssignment.Randomized(53000, 49200), assignment)
        assertEquals(listOf(49200, 51000, 52000, 53000), seen)
        assertEquals(53000, store.value)
    }

    // ---- 重试上限 ----

    @Test
    fun exhaustedRandomRetriesFailWithExplicitReason() {
        val store = InMemoryPortStore(50000)
        val seen = mutableListOf<Int>()
        val assignment =
            RemotePortPolicy(store) { _, _ -> 60000 }.resolve { port ->
                seen.add(port)
                PortBindAttempt.Occupied
            }
        val failure = assignment as PortAssignment.Failed
        assertTrue("失败原因应含上次端口：${failure.reason}", failure.reason.contains("50000"))
        assertTrue("失败原因应明示重试耗尽：${failure.reason}", failure.reason.contains("均被占用"))
        assertEquals("持久化端口 1 次 + 随机重试 3 次 = 4 次绑定", 4, seen.size)
        assertEquals("失败路径不改动持久化值", 50000, store.value)
    }

    @Test
    fun retryLimitIsExactlyThreeRandomAttemptsWithoutPersistedValue() {
        val store = InMemoryPortStore()
        val seen = mutableListOf<Int>()
        val assignment =
            RemotePortPolicy(store) { _, _ -> 61000 }.resolve { port ->
                seen.add(port)
                PortBindAttempt.Occupied
            }
        assertTrue(assignment is PortAssignment.Failed)
        assertEquals("有限重试 = 3 次随机（决策 63 下限）", 3, seen.size)
        assertEquals("全部被占不持久化", null, store.value)
    }

    @Test
    fun defaultRandomCandidatesStayInDynamicSegment() {
        // 生产默认随机源：候选端口必须全部落在动态段 49152–65535（含端点）
        val store = InMemoryPortStore()
        val seen = mutableListOf<Int>()
        val assignment = RemotePortPolicy(store).resolve { port ->
            seen.add(port)
            PortBindAttempt.Occupied
        }
        assertTrue(assignment is PortAssignment.Failed)
        assertEquals(RemotePortPolicy.RANDOM_RETRY_LIMIT, seen.size)
        seen.forEach {
            assertTrue("候选端口应在动态段：$it", it in RemotePortPolicy.MIN_PORT..RemotePortPolicy.MAX_PORT)
        }
    }

    // ---- 非占用失败即停（不静默、不端口重试）----

    @Test
    fun nonOccupiedFailureAbortsImmediatelyWithoutRetry() {
        val store = InMemoryPortStore()
        val seen = mutableListOf<Int>()
        val assignment = RemotePortPolicy(store).resolve { port ->
            seen.add(port)
            PortBindAttempt.Unavailable("未找到可绑定的局域网 IPv4 接口")
        }
        assertEquals(PortAssignment.Failed("未找到可绑定的局域网 IPv4 接口"), assignment)
        assertEquals("非占用失败不做端口重试", 1, seen.size)
        assertEquals("失败不持久化", null, store.value)
    }

    @Test
    fun nonOccupiedFailureOnReuseAttemptAbortsBeforeRandom() {
        val store = InMemoryPortStore(51000)
        val seen = mutableListOf<Int>()
        val assignment = RemotePortPolicy(store).resolve { port ->
            seen.add(port)
            PortBindAttempt.Unavailable("绑定失败：其他 IO 错误")
        }
        assertEquals(PortAssignment.Failed("绑定失败：其他 IO 错误"), assignment)
        assertEquals("复用尝试非占用失败直接终止，不进入随机", listOf(51000), seen)
    }

    // ---- 越界持久化值 ----

    @Test
    fun outOfRangePersistedValueIsTreatedAsAbsent() {
        for (bad in listOf(0, -1, 80, 49151, 65536)) {
            val store = InMemoryPortStore(bad)
            val seen = mutableListOf<Int>()
            val assignment =
                RemotePortPolicy(store) { _, _ -> 50000 }.resolve { p ->
                    seen.add(p)
                    PortBindAttempt.Bound(p)
                }
            assertEquals("越界值 $bad 应回退随机分配", PortAssignment.Randomized(50000, null), assignment)
            assertEquals("越界值 $bad 不参与复用尝试", listOf(50000), seen)
        }
    }

    // ---- 动态段边界传参 ----

    @Test
    fun randomGeneratorReceivesInclusiveDynamicSegmentBounds() {
        var captured: Pair<Int, Int>? = null
        val store = InMemoryPortStore()
        RemotePortPolicy(store) { min, max ->
            captured = min to max
            min
        }.resolve { PortBindAttempt.Unavailable("终止") }
        assertEquals(
            "随机入参应为闭区间动态段（下界, 上界）",
            RemotePortPolicy.MIN_PORT to RemotePortPolicy.MAX_PORT,
            captured,
        )
    }
}

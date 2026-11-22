package com.sofatype.ime.dicts

import com.sofatype.pinyin.DictFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File

class DictPackStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun entry(
        name: String = "样例热词包",
        bytes: Long,
        sha: String,
        version: String = "1.0",
    ): DictPackEntry = DictPackEntry(name, version, sha, bytes, "http://example.com/$name.bin")

    private fun packBytes(): ByteArray {
        val data = com.sofatype.pinyin.DictData(
            listOf("xian", "yan", "bao"),
            listOf("显眼包"),
            listOf(com.sofatype.pinyin.DictData.Entry(intArrayOf(0, 1, 2), 0, 100.0)),
        )
        return ByteArrayOutputStream().also { DictFormat.write(data, it) }.toByteArray()
    }

    @Test
    fun `安装校验通过后落盘bin与meta_回读一致且isVerified成立`() {
        val store = DictPackStore(tmp.newFolder("dicts"))
        val bytes = packBytes()
        val download = File(tmp.root, "stage.bin").apply { writeBytes(bytes) }
        val packEntry = entry(bytes = bytes.size.toLong(), sha = DictPackStore.sha256Hex(download))

        assertTrue(store.install(packEntry, download))
        assertFalse("下载暂存文件应被收走", download.exists())
        assertTrue(store.packFile(packEntry.name).isFile)
        assertTrue(store.isVerified(packEntry.name))
        assertEquals(packEntry, store.installedEntry(packEntry.name))
        assertEquals(listOf(packEntry), store.installedEntries())
    }

    @Test
    fun `sha不符或字节数不符_安装被拒_旧词包保留`() {
        val store = DictPackStore(tmp.newFolder("dicts"))
        val bytes = packBytes()
        val goodDownload = File(tmp.root, "good.bin").apply { writeBytes(bytes) }
        val goodEntry = entry(bytes = bytes.size.toLong(), sha = DictPackStore.sha256Hex(goodDownload))
        assertTrue(store.install(goodEntry, goodDownload))

        val tampered = File(tmp.root, "tampered.bin").apply { writeBytes(bytes.clone().also { it[0] = it[0].inc() }) }
        val badShaEntry = entry(bytes = tampered.length(), sha = goodEntry.sha256Hex)
        assertFalse("sha 不符应被拒", store.install(badShaEntry, tampered))
        assertFalse("暂存文件失败后应清理", tampered.exists())
        assertTrue("旧词包应保留", store.isVerified(goodEntry.name))

        val wrongSize = File(tmp.root, "short.bin").apply { writeBytes(bytes.copyOfRange(0, bytes.size - 2)) }
        val wrongSizeEntry = entry(bytes = bytes.size.toLong(), sha = DictPackStore.sha256Hex(wrongSize) + "00")
        assertFalse("字节数不符应被拒", store.install(wrongSizeEntry, wrongSize))
    }

    @Test
    fun `勾选集合持久化_跨实例回读一致`() {
        val dir = tmp.newFolder("dicts")
        val storeA = DictPackStore(dir)
        storeA.setEnabled("样例热词包", true)
        storeA.setEnabled("另一包", false)

        val storeB = DictPackStore(dir)
        assertTrue(storeB.isEnabled("样例热词包"))
        assertFalse(storeB.isEnabled("另一包"))
        assertFalse(storeB.isEnabled("不存在的包"))
    }

    @Test
    fun `loadDictData_未装或缺meta返回null_损坏bin抛出由调用方兜底`() {
        val store = DictPackStore(tmp.newFolder("dicts"))
        assertNull(store.loadDictData("样例热词包"))

        val bytes = packBytes()
        val download = File(tmp.root, "stage.bin").apply { writeBytes(bytes) }
        val packEntry = entry(bytes = bytes.size.toLong(), sha = DictPackStore.sha256Hex(download))
        assertTrue(store.install(packEntry, download))

        val data = store.loadDictData(packEntry.name)
        assertEquals("显眼包", data?.words?.single())

        store.packFile(packEntry.name).writeBytes(ByteArray(16) { 0x55 })
        assertNull("长度不符直接判失效", store.loadDictData(packEntry.name))

        val corrupted = packBytes().clone().also { it[12] = 0x00 }
        store.packFile(packEntry.name).writeBytes(corrupted)
        val broken = runCatching { store.loadDictData(packEntry.name) }
        assertTrue("同长度坏包应抛出（调用方跳过兜底）", broken.isFailure)
    }

    @Test
    fun `meta缺失或损坏时installedEntries为空_不影响引擎回退`() {
        val dir = tmp.newFolder("dicts")
        val store = DictPackStore(dir)
        File(dir, "x.meta").writeText("不是JSON")
        File(dir, "y.meta").writeText("""[{"name":"y","version":"1","sha256":"${"0".repeat(64)}","bytes":1,"url":"http://e.com/y.bin"}]""")
        assertEquals(1, store.installedEntries().size)
        assertNull(store.installedEntry("z"))
    }
}

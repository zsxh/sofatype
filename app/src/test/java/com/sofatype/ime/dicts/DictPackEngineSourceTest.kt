package com.sofatype.ime.dicts

import com.sofatype.pinyin.DictData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File

class DictPackEngineSourceTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun installPack(store: DictPackStore, name: String, enable: Boolean) {
        val data = DictData(
            listOf("xian", "yan", "bao"),
            listOf("显眼包"),
            listOf(DictData.Entry(intArrayOf(0, 1, 2), 0, 100.0)),
        )
        val bytes = ByteArrayOutputStream().also { com.sofatype.pinyin.DictFormat.write(data, it) }.toByteArray()
        val download = File(tmp.root, "$name.stage").apply { writeBytes(bytes) }
        val entry = DictPackEntry(name, "1.0", DictPackStore.sha256Hex(download), bytes.size.toLong(), "http://e.com/$name.bin")
        assertTrue(store.install(entry, download))
        store.setEnabled(name, enable)
    }

    @Test
    fun `仅装载勾选且校验通过的词包_未勾选不装载`() {
        val store = DictPackStore(tmp.newFolder("dicts"))
        installPack(store, "包A", enable = true)
        installPack(store, "包B", enable = false)

        val source = DictPackEngineSource(store)
        val packs = source.loadEnabled()

        assertEquals(1, packs.size)
        assertEquals("显眼包", packs.single().words.single())
    }

    @Test
    fun `勾选但文件损坏的词包跳过并回调原因_其余词包不受影响`() {
        val store = DictPackStore(tmp.newFolder("dicts"))
        installPack(store, "包A", enable = true)
        installPack(store, "坏包", enable = true)
        store.packFile("坏包").writeBytes(ByteArray(61) { 0x11 })

        val skipped = mutableListOf<Pair<String, String>>()
        val source = DictPackEngineSource(store) { name, reason -> skipped.add(name to reason) }
        val packs = source.loadEnabled()

        assertEquals(1, packs.size)
        assertEquals(1, skipped.size)
        assertEquals("坏包", skipped.single().first)
        assertTrue(skipped.single().second.isNotBlank())
    }
}

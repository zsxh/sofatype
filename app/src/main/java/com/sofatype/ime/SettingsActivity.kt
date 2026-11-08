// T20 · 设置页补全（FR-D1~D3、D5，决策 19/21）。
// 范围边界：检查更新三态逻辑属 T21（FR-D4），「检查更新」按钮仅静态结构不接行为；测试输入区沿 T2，仅 debug 显示。
package com.sofatype.ime

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView

class SettingsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // FR-D3：版本信息卡读真实版本（决策 19：versionName/versionCode 以构建配置为准）
        findViewById<TextView>(R.id.version_value).text = readVersionText()

        val testArea = findViewById<View>(R.id.test_input_area)
        // 决策 21：测试输入区仅 debug 构建显示，release 与设计稿一致
        if (BuildConfig.DEBUG) {
            testArea.visibility = View.VISIBLE
            val input = findViewById<EditText>(R.id.test_input)
            // 「填入示例文本」：预置确定性文本，供键盘/上屏验收（后续 T3/T16）使用
            findViewById<Button>(R.id.btn_fill_sample).setOnClickListener {
                input.setText(R.string.sample_text)
                input.setSelection(input.text.length)
            }
            // 「全选」：制造选中区间，供替换选中区间类验收（T16）使用
            findViewById<Button>(R.id.btn_select_all).setOnClickListener {
                input.selectAll()
            }
        } else {
            testArea.visibility = View.GONE
        }
    }

    // FR-D3：「v<versionName> (<versionCode>)」。versionCode 字段 API 28 起标废弃，
    // longVersionCode 需 API 28（minSdk 23 不可用），故沿用 versionCode 字段并压制告警。
    @Suppress("DEPRECATION")
    private fun readVersionText(): String {
        val info = packageManager.getPackageInfo(packageName, 0)
        return VersionFormatter.format(info.versionName, info.versionCode)
    }
}

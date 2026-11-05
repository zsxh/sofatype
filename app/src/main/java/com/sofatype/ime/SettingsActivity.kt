// T2 · 设置页壳子（PRD 决策 21 / FR-D1 最小集）。
// 范围边界：品牌区、版本信息卡、底部操作提示属 T20；占位键盘属 T3，本类不涉及输入法服务。
package com.sofatype.ime

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText

class SettingsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val testArea = findViewById<View>(R.id.test_input_area)
        // 决策 21：测试输入区仅 debug 构建显示，release 与设计稿一致（T20 前仅面包屑 + 背景）
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
}

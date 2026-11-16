// T63 · 测试打字页门控决策（纯 Kotlin，无框架依赖，单元测试锁定）。
// 门控口径核对记录（PRD_20261112 FR-D1 细项 4「沿用现行调试区实现，开工时核对并如实记录」）：
// 现行调试区实现 = 设置页 test_input_area 以 BuildConfig.DEBUG 置 visibility
//（PRD_20261104 决策 21：~~测试输入区仅 debug 构建显示，release 与设计稿一致~~）。
// T63 迁移后该口径原样沿用于测试打字页 test_typing_content：~~debug 构建显示三件套，
// release 构建整体隐藏（页面仅剩面包屑与底部提示）~~（→ 以上两段被 T79 · 决策 65 取代：
// 三件套 debug / release 一致显示，消除 release「入口在、页空」；设置页正式功能面口径不变）。
// 「测试打字」入口按钮不在门控内：为设置卡正式新增行（画板 7 第三行，FR-D1 细项 1
// 仅约束样式），debug / release 一致显示。
package com.sofatype.ime

object TestTypingGate {

    /**
     * 测试三件套是否可见（T79 · 决策 65：debug / release 均显示）。
     * isDebugBuild 参数保留：调用点签名与门控形态不变，单测按两态锁定放开后语义。
     */
    fun contentVisible(isDebugBuild: Boolean): Boolean = true
}

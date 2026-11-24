# tools/wordpack · 热词源刷新工具（T96，T101 换开源源）

本目录是构建机侧词源刷新工具。抓取与整理热词源，产物入库 `pinyin/dictsrc/hotwords/`。
gradle 构建期零网络；词源抓取只发生在本机刷新时。

## 入库词源（pinyin/dictsrc/hotwords/）

| 文件 | 说明 |
| --- | --- |
| `thuocl-open.txt` | THUOCL 清华大学开放中文词库派生（MIT，Copyright 2018 THUNLP）：上游 10 域词表合并去重、DF 降序前 12000、pypinyin 注音（`fetch_thuocl.py` + `build_thuocl_source.py` 确定性派生，重跑字节一致） |
| `nlc-netlang-years.txt` | 国家语言资源监测中心年度十大网络用语（2012 起）注音行 |
| `yaowenjiaozi-years.txt` | 《咬文嚼字》年度十大流行语（2008 起）注音行 |
| `sources.sha256` | 全部热词源 sha256 锁定文件；构建期 `verifyHotDict` 门禁 + 单测 `HotDictSourceLockTest` 双核对 |

T101 出库（git 历史留证）：搜狗 scel「网络流行新词」（官方条款限个人使用，公开仓库冲突）、
百度沸点历年榜与百度实时榜（无单独许可；实时榜 T99 起已停止并入）。
搜狗 scel 解析能力保留在 `pinyin` 模块（`ScelDictParser`，测试覆盖）。

注音行格式：`词 频次 0 拼音…`（频次固定 3000；拼音由 pypinyin 生成，一词一音节）。
词表条目规则：纯汉字、1~6 字；字母数字混合条目（如「栓Q」「YYDS」「city不city」）无法按拼音输入，不入词表。
频次口径（T99 起）：构建期按词长分档重写为绝对频次档位（`HotDictBuild.tierFreq`：2~3 字 2500.0，4 字以上 300.0），源文件保留原始注音频次。
许可与逐源评估结论见 `docs/DICTS.md`。

## 刷新（年更，发版前人工执行一次）

```bash
nix develop -c bash tools/wordpack/refresh_hotwords.sh
```

步骤：①THUOCL 上游域词表重取（10 域）→ ②确定性派生 `thuocl-open.txt` → ③重算 `sources.sha256` → ④`verifyHotDict` 门禁复验 → ⑤人工整理当年语监中心 / 咬文嚼字榜单并入 `*_years.txt`。核对 `git diff` 后随发版批次提交。

## 构建接线

- 热词源 → SPD1：`:pinyin:buildHotDict`（合并去重、查主词库去重、档位频次重写、确定性产物，见 `pinyin/build.gradle.kts`）。
- 逐词库开关：`-PsofatypeHotDicts=<id,…>`；默认全内置。用法见 `docs/DICTS.md`。
- 运行时：APK 内置 `assets/dict/hotwords.bin`，引擎装载时按包内频次原样并入（`PinyinEngineLoader` + `DictHotMerge`，T99 起不再 ×10）。

## 单测

`ScelDictParserTest`（scel 解析）、`HotDictBuildTest`（合并去重、超限跳过、同源重跑 sha256 一致）、
`HotDictSourceLockTest`（源文件 sha256 门禁）、`DictHotMergeTest` / `DictHotRealDictTest`（运行时合并与加权排序）。
跑法：`nix develop -c ./gradlew --no-daemon :pinyin:test --rerun-tasks`。

## 词源抓取端点

| 端点 | 说明 |
| --- | --- |
| `https://raw.githubusercontent.com/thunlp/THUOCL/master/data/THUOCL_<域>.txt` | THUOCL 上游域词表（`fetch_thuocl.py`；上游 MIT，可公开再分发） |

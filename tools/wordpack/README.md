# tools/wordpack · 热词源刷新工具（T96）

本目录是构建机侧词源刷新工具。抓取与整理热词源，产物入库 `pinyin/dictsrc/hotwords/`。
gradle 构建期零网络；词源抓取只发生在本机刷新时。

## 入库词源（pinyin/dictsrc/hotwords/）

| 文件 | 说明 |
| --- | --- |
| `sogou-liuxingxinci.scel` | 搜狗官方细胞词库「网络流行新词」累积库原件（ECS 头，自带拼音，官方周更；sha256 见 `sources.sha256`） |
| `baidu-boiling-years.txt` | 百度沸点 / 百度搜索风云榜历年榜单人工整理注音行（2009 起逐年；年份顺序为新在前） |
| `nlc-netlang-years.txt` | 国家语言资源监测中心年度十大网络用语（2012 起）注音行 |
| `yaowenjiaozi-years.txt` | 《咬文嚼字》年度十大流行语（2008 起）注音行 |
| `baidu-realtime-days.txt` | 百度实时热搜榜多日积累注音行。**T99 起停止并入词库**（新闻标题片段非词、时效差），文件与抓取脚本保留供复评，不参与默认构建 |
| `sources.sha256` | 全部热词源 sha256 锁定文件；构建期 `verifyHotDict` 门禁 + 单测 `HotDictSourceLockTest` 双核对 |

注音行格式：`词 频次 0 拼音…`（频次固定 3000.0；拼音由 pypinyin 生成，一词一音节）。
词表条目规则：纯汉字、1~6 字；字母数字混合条目（如「栓Q」「YYDS」「city不city」）无法按拼音输入，不入词表。
频次口径（T99 起）：构建期按词长分档重写为绝对频次档位（`HotDictBuild.tierFreq`：2~3 字 2500.0，4 字以上 300.0），源文件保留原始注音频次。
许可与逐源评估结论见 `docs/DICTS.md`。

## 刷新（年更，发版前人工执行一次）

```bash
nix develop -c bash tools/wordpack/refresh_hotwords.sh
```

步骤：①scel 整包重抓（需先访问详情页取会话，直链会被拒绝）→ ②重算 `sources.sha256` → ③`verifyHotDict` 门禁复验 → ④人工整理当年沸点 / 咬文嚼字 / 语监中心榜单并入 `*_years.txt`。核对 `git diff` 后随发版批次提交。
百度实时榜抓取链（T99 起停止并入）保留在目录内供复评，不在刷新链中执行。

## 构建接线

- 热词源 → SPD1：`:pinyin:buildHotDict`（合并去重、查主词库去重、档位频次重写、确定性产物，见 `pinyin/build.gradle.kts`）。
- 逐词库开关：`-PsofatypeHotDicts=<id,…>`；默认全内置（可选 id 不含已停用的 baidu-realtime）。用法见 `docs/DICTS.md`。
- 运行时：APK 内置 `assets/dict/hotwords.bin`，引擎装载时按包内频次原样并入（`PinyinEngineLoader` + `DictHotMerge`，T99 起不再 ×10）。

## 单测

`ScelDictParserTest`（scel 解析）、`HotDictBuildTest`（合并去重、超限跳过、同源重跑 sha256 一致）、
`HotDictSourceLockTest`（源文件 sha256 门禁）、`DictHotMergeTest` / `DictHotRealDictTest`（运行时合并与加权排序）。
跑法：`nix develop -c ./gradlew --no-daemon :pinyin:test --rerun-tasks`。

## 词源抓取端点

| 端点 | 说明 |
| --- | --- |
| `https://pinyin.sogou.com/dict/detail/index/4` → `download_cell.php?id=4&…` | 搜狗细胞词库「网络流行新词」（`fetch_sogou_scel.py`；直链访问返回 HTML，须先 GET 详情页取会话） |
| `https://top.baidu.com/board?tab=realtime` | 百度热搜官方榜单页，数据为页面内嵌 `<!--s-data:{JSON}-->`（`fetch_hot_search.py` 等；T99 起停止并入，脚本保留供复评） |

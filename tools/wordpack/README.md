# tools/wordpack · 词包流水线（T95）

从原料（搜狗细胞词库 scel + 百度热搜自动筛词）产出单个 SPD1 热词包 + manifest。
构建机工具链，产物不入 APK；主词库 `ConvertDict` / `VerifyDict` / sha256 门禁零改动。

## 原料

| 文件 | 说明 |
| --- | --- |
| `fixtures/sogou-wangluoliuxingxinci.scel` | 搜狗官方细胞词库「网络流行新词」样本（276,283 字节，2026-11-22 取自 `pinyin.sogou.com/d/dict/download_cell.php?id=4`，ECS 头格式） |
| `fixtures/sogou-tianwencihui.scel` | 搜狗官方细胞词库「天文词汇大全」样本（89,866 字节，同渠道 id=15204，DCS 头格式） |
| `fixtures/baidu-hot-search-sample.json` | 百度热搜官方榜单页内嵌 s-data JSON 样本快照（2026-11-22 抓取，51 词条；仅作链路输入样本，非实时端点响应） |

scel 版权口径：官方条款限个人使用（T95 口径 4，户主 2026-11-22 拍板），私人家用使用，产物不对外分发。

热搜端点：`https://top.baidu.com/board?tab=realtime`，数据为页面内嵌 `<!--s-data:{JSON}-->`；
该地址 API 路径 `/api/platform/getBoard` 实测 404，抓取走页面内嵌数据块（`fetch_hot_search.py`）。

## 链路

1. `fetch_hot_search.py`：抓端点 → s-data JSON 快照（离线验证可改用 fixtures 样本快照）。
2. `screen_hot_words.py`：自动筛词。规则：跳过置顶运营位（`isTop`）；标题按标点/空白切分；
   片段须纯汉字、2~6 字（6 字上限 = SPD1 单词条音节数上限）；榜单原序去重；取前 30。
3. `hot_search_annotate.py`：筛出词经 pypinyin 注音为源文本行 `词 频次 0 拼音…`（频次固定 3000.0；
   拼音不可用即跳过并计数）。scel 自带拼音，免注音。
4. `./gradlew :pinyin:wordPackBuild`：两路汇合——scel 词条全量并入，热搜词查主词库
   （`pinyin/dictsrc/rawdict_utf16_65105_freq.txt`）去重、跨原料去重，转 SPD1 并生成 manifest。

## 跑法

```bash
# 本地完整流水线（默认版本号 = 当日日期.1；含环境重定向，见仓库本地开发指引「构建与测试」节）
bash tools/wordpack/build_wordpack.sh
# 指定版本号与输出目录
bash tools/wordpack/build_wordpack.sh 20261122.1 tools/wordpack/dist
# 确定性重跑（固定原料：热搜改用 fixtures 样本快照；产物 sha256 须与首跑一致）
HOT_SNAPSHOT=tools/wordpack/fixtures/baidu-hot-search-sample.json \
  nix develop -c bash tools/wordpack/pipeline.sh 20261122.1 tools/wordpack/dist-check
```

产物（`tools/wordpack/dist/`，gitignore 不入库）：

| 文件 | 说明 |
| --- | --- |
| `baidu-hot-search-snapshot.json` | 本轮热搜原料快照 |
| `hot-words-screened.txt` / `hot-words-annotated.txt` | 筛词与注音中间产物 |
| `wordpack.bin` | SPD1 热词包（复用 `DictFormat`，词 + 拼音 + 权重） |
| `manifest.json` | 词包清单，字段 name / version / sha256 / bytes / url |

manifest 口径：`version` 格式 `YYYYMMDD.N`；`sha256` 与 `bytes` 对应 `wordpack.bin`；
`url` 固定空串 = 分发 URL 未配置（T94 追加补充同口径，待户主提供后回填重出）。
已知缺口：当前 app 侧 `DictPackManifestParser` 要求 `url` 非空 http(s)，空串 manifest
在「分发源未配置」态加载会回退纯主词库，不阻塞输入主链路。

## 单测与 CI

- 转换核心单测：`ScelDictParserTest`（scel 解析：ECS/DCS/SCEL 头、同音词组、附加区、坏词跳过、
  魔数校验）、`WordPackBuildTest`（注音去重合并、SPD1 往返、manifest 生成与确定性）。跑法：
  `nix develop -c ./gradlew --no-daemon :pinyin:test --rerun-tasks`。
- GitHub Actions：`.github/workflows/wordpack.yml`，仅 `workflow_dispatch` 手动触发
  （户主拍板暂不定时），CI 装 JDK 17 + Python + pypinyin 后跑 `pipeline.sh`，产物上传 artifact，
  不发布 Release。该 workflow 仓库暂无远端，未实测。
- 本机兜底：`build_wordpack.sh`（环境重定向 + nix develop 内跑 pipeline.sh）。

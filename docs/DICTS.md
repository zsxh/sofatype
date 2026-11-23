# DICTS · 词库档案

sofatype 内置词库的清单、体量、更新方式与构建选择。词库全部构建期内置 APK；运行时零词库网络请求。

## 词库清单

| id | 词库 | 来源 | 范围 | 词数 | 大小 | 许可 |
| --- | --- | --- | --- | --- | --- | --- |
| main | AOSP 谷歌拼音词库（主词库） | AOSP 输入法随附词表 | 全领域常用词 | 65 105 行（去重词 64 406） | 源 3 487 KB | Apache-2.0 |
| sogou | 搜狗「网络流行新词」 | 搜狗细胞词库官方站 id=4 | 网络流行新词多年累积库 | 解析 11 480 | 270 KB | 官方条款限个人使用（私人家用） |
| baidu-boiling | 百度沸点 / 搜索风云榜历年榜 | 百度官方年度榜单 | 2009-2023 年度热词 | 138 | 4.4 KB | 无单独许可（事实性榜单） |
| nlc-netlang | 语监中心年度网络用语 | 官方发布页转录 | 2012-2025 年度十大网络用语 | 97 | 3.4 KB | 无单独许可（官方公开词表） |
| yaowenjiaozi | 咬文嚼字年度十大流行语 | 编辑部年度发布 | 2008-2025 每年十大流行语 | 155 | 4.3 KB | 无单独许可（编辑部词表） |
| baidu-realtime | 百度实时热搜榜（**T99 起停止并入**） | `top.baidu.com` 实时榜（脚本筛词注音） | 近期热搜词累积 | 60 | 2.2 KB | 无单独许可（事实性榜单） |

说明：主词库源文件与热词源文件随仓库入库，`sources.sha256` 锁定。字母数字混合词与超 6 音节词不入词表。baidu-realtime 源文件与抓取脚本保留供复评，不参与默认构建（T99）。
热词频次口径（T99）：构建期按词长分档重写为绝对频次档位——2~3 字档 2500.0、4 字以上档 300.0，抹平搜狗 scel 原始频次差异；运行时合并按包内频次原样并入，不再「原始频次 ×10」。

## 体量合计

| 产物 | 词条 | 大小 |
| --- | --- | --- |
| 主词库 `pinyin_dict.bin` | 65 105（去重词 64 406） | 1 478 KB |
| 高频子集 `pinyin_dict_highfreq.bin`（首屏装载用） | 4 000 | 88 KB |
| 热词包 `hotwords.bin`（上表 4 个并入源合并去重） | 9 076 | 235 KB |

运行时热词并入主词库后：71 110 词条，去重词 70 408。热词包使 APK 增量 235 KB。

## 更新词库

统一入口：

```bash
nix develop -c bash tools/wordpack/refresh_hotwords.sh
```

- 自动：搜狗累积库整包重抓；重算 `sources.sha256`；`:pinyin:verifyHotDict` 门禁复验。
- 人工：百度沸点 / 咬文嚼字 / 语监中心年度榜发布后，人工转录为注音行并入对应 `*_years.txt`，再重跑该脚本过门禁。
- 主词库换源另立项。运行时零词库网络请求。

## 构建时选择词库

- 构建属性 `sofatypeHotDicts` = 逗号分隔的热词源 id（取清单表 id 列；`baidu-realtime` 已停止并入，选它按未知 id 构建失败）。
- 不设置 = 主词库 + 全部并入源（默认）；空值 = 不内置热词（回退纯主词库）；未知 id = 构建失败；主词库始终内置。

```bash
# 默认全内置
nix develop -c ./gradlew --no-daemon assembleDebug
# 仅搜狗 + 沸点历年
nix develop -c ./gradlew --no-daemon assembleDebug -PsofatypeHotDicts=sogou,baidu-boiling
# 不内置热词
nix develop -c ./gradlew --no-daemon assembleDebug "-PsofatypeHotDicts="
```

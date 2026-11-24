# DICTS · 词库档案

sofatype 内置词库的清单、体量、更新方式与构建选择。词库全部构建期内置 APK；运行时零词库网络请求。

## 词库清单

| id | 词库 | 来源 | 范围 | 词数 | 大小 | 许可 |
| --- | --- | --- | --- | --- | --- | --- |
| main | AOSP 谷歌拼音词库（主词库） | AOSP 输入法随附词表 | 全领域常用词 | 65 105 行（去重词 64 406） | 源 3 487 KB | Apache-2.0 |
| thuocl-open | THUOCL 清华开放中文词库（T101 起） | github.com/thunlp/THUOCL（MIT） | 10 域领域词（IT / 财经 / 成语 / 地名 / 历史名人 / 医学 / 饮食 / 法律 / 汽车 / 动物），DF 前 12000 派生 | 派生 12 000 行 | 源 372 KB | MIT（Copyright 2018 THUNLP，学术引用格式见 NOTICE） |
| nlc-netlang | 语监中心年度网络用语 | 官方发布页转录 | 2012-2025 年度十大网络用语 | 97 | 3.4 KB | 无单独许可（官方公开词表） |
| yaowenjiaozi | 咬文嚼字年度十大流行语 | 编辑部年度发布 | 2008-2025 每年十大流行语 | 155 | 4.3 KB | 无单独许可（编辑部词表） |

说明：主词库源文件与热词源文件随仓库入库，`sources.sha256` 锁定。字母数字混合词与超 6 音节词不入词表。
T101 出库记录（git 历史留证）：搜狗「网络流行新词」scel（官方条款限个人使用，与公开仓库再分发冲突）、百度沸点历年榜与百度实时热搜榜（无单独许可；实时榜 T99 起停止并入）。热词频道语义由「网络流行新词」变为「开放领域词库补强」。
THUOCL 派生口径（T101）：上游 10 域合并、跨域去重（同词取最大 DF）、纯汉字 1~6 字过滤、DF 降序前 12000、pypinyin 注音；派生确定性（重跑字节一致）；与主词库重叠词条在构建期并入时去重（本轮命中去重 4 543 行、跨源去重 29 行）。
热词频次口径（T99）：构建期按词长分档重写为绝对频次档位——2~3 字档 2500.0、4 字以上档 300.0，抹平各源原始频次差异；运行时合并按包内频次原样并入，不再「原始频次 ×10」。

## 体量合计

| 产物 | 词条 | 大小 |
| --- | --- | --- |
| 主词库 `pinyin_dict.bin` | 65 105（去重词 64 406） | 1 478 KB |
| 高频子集 `pinyin_dict_highfreq.bin`（首屏装载用） | 4 000 | 88 KB |
| 热词包 `hotwords.bin`（上表 3 个并入源合并去重后） | 7 680 | 236 KB |

运行时热词并入主词库后：72 785 词条，去重词 72 086（热词包构建期已查主库去重，包内词均为主库外新词，运行时合并零损耗）。热词包使 APK 增量 236 KB。

## 更新词库

统一入口：

```bash
nix develop -c bash tools/wordpack/refresh_hotwords.sh
```

- 自动：THUOCL 上游 10 域词表重取 → 确定性派生 `thuocl-open.txt` → 重算 `sources.sha256` → `:pinyin:verifyHotDict` 门禁复验。
- 人工：语监中心 / 咬文嚼字年度榜发布后，人工转录为注音行并入对应 `*_years.txt`，再重跑该脚本过门禁。
- 主词库换源另立项。运行时零词库网络请求。

## 构建时选择词库

- 构建属性 `sofatypeHotDicts` = 逗号分隔的热词源 id（取清单表 id 列）。
- 不设置 = 主词库 + 全部并入源（默认）；空值 = 不内置热词（回退纯主词库）；未知 id = 构建失败；主词库始终内置。

```bash
# 默认全内置
nix develop -c ./gradlew --no-daemon assembleDebug
# 仅 THUOCL + 语监中心
nix develop -c ./gradlew --no-daemon assembleDebug -PsofatypeHotDicts=thuocl-open,nlc-netlang
# 不内置热词
nix develop -c ./gradlew --no-daemon assembleDebug "-PsofatypeHotDicts="
```

# Bingo! 26.3

Minecraft **26.3**（Fabric）的自研 Bingo 引擎。工程不依赖 YAMB 或其他玩法模组：棋盘、队伍、大厅、随机地图、任务判定、迷雾、追杀、随机事件、团队仓库、UI 全部由本工程实现；只搭配工具型前置（Fabric API、YACL、Mod Menu、JEI、ReplayMod、性能优化 Mod）。小地图与世界地图为自研，不依赖 Xaero。

> 产物：一个服客一体的 Mod + 内置数据包/材质 + 固定版本整合包与服务端包。

---

> 仓库只提交源码、数据、脚本与文档；`mods/` 下的参考 jar、`dist/` 下的构建产物不入库。固定版本与下载地址见 `tools/pack_manifest.json`。

## 0. 使用说明

完整 PDF 使用手册：[`docs/Bingo-26.3-使用说明.pdf`](docs/Bingo-26.3-使用说明.pdf)（安装、快速开始、规则、命令、配置、地图、故障排查）。

## 1. 功能总览

| # | 需求 | 实现 |
|---|------|------|
| 1 | 26.3 可运行的 Bingo | 自研 `BingoGame` 状态机：大厅 → 倒计时 → 对局 → 结算；5×5/7×7/9×9 棋盘 |
| 2 | 可控大厅、配置、每局随机地图 | 主世界大厅平台 + 独立 `bingo_ourselves:arena` 维度；**每局删除旧维度并用新随机种子重建**；`/bingo` 全量配置 |
| 3 | 自带 JEI，可关闭 | 整合包携带 JEI；YACL 设置中的 `JEI overlay` 开关运行时关闭覆盖层 |
| 4 | 背包整理和箱子整理 | 自研排序：在背包/箱子界面按 `R`，服务端合并、排序并同步；可在设置中关闭 |
| 5 | 小地图/世界地图，可关闭 | 自研地形小地图 + 全屏世界地图；按维度与每局随机种子自动清空缓存，无需手动增删地图；设置中分别开关 |
| 6 | 中英双语 | 引擎文案全部 `en_us`/`zh_cn`；121 个任务进度标题/描述由 26.3 原版双语语料生成 |
| 7 | 兼容多人优化 | 携带 Lithium、Krypton、C2ME、FerriteCore；判定全部服务端权威 |
| 8 | 客户端可调 UI | YACL 设置（`O` / Mod Menu）：棋盘开关、四角锚点、X/Y 偏移、缩放、透明度、背景、计时器、F3 隐藏；另有棋盘详情页 |
| 9 | 128 堆叠 | `ItemInstanceMixin` 注入 `getMaxStackSize()`，`/bingo stack` 可验证 |
| 10 | 覆盖原版进度并替换 | `pack.mcmeta` 过滤器屏蔽五个原版可见进度目录；提供 5 个标签页、121 个任务的新进度树 |
| 11 | 鞘翅 + 胸甲同时装备 | `InventoryMenu` 增加独立鞘翅槽（`/bingo` 无用，直接拖入）；`LivingEntity` 混入允许额外鞘翅滑翔并消耗耐久 |
| 12 | 自带 Replay | 整合包携带 ReplayMod 26.3，可按其快捷键录制 |
| 13 | 每个棋盘独立难度、可自定义 | `/bingo boards N`、`/bingo board <i> difficulty <easy|medium|hard|extreme|custom>`、`/bingo distribution S A B C D` |

### 拓展内容

- **追杀玩法**：`/bingo hunter on` + `preset hunter`；猎人队伍追踪最近跑者，跑者有限生命，生命耗尽出局；跑者完成 Bingo 获胜，否则猎人获胜。
- **永昼 / 永夜 / 固定天气**：`/bingo daylight day|night|cycle`、`/bingo weather clear|rain|thunder|cycle`。
- **补给空投**：定时在每队附近生成带战利品的箱子；`/bingo supply` 手动触发。
- **随机事件**：怪物潮、物品雨、随机传送、随机状态；`/bingo event <mobs|rain|teleport|effects|random>`。
- **团队共享仓库**：`/bingo chest` 或大厅箱子物品打开 27 格共享箱子，内容随对局/重启持久化。
- **迷雾棋盘**：`/bingo fog on`；初始只揭示中心格，完成一格后解锁上下左右相邻格。

---

## 2. 架构

```
bingo-ourselves-1.0.0.jar（服客一体）
├── game/
│   ├── BingoGame          对局状态机、队伍、计时、胜负
│   ├── ArenaManager       独立维度创建/删除、每局随机种子、队伍出生点
│   ├── BingoBoard         棋盘、连线、完成度、锁定归属
│   ├── ObjectiveRegistry  从数据包加载 1503 个物品 + 121 个进度任务
│   ├── CompletionService  背包扫描 + 进度完成判定 + 迷雾解锁
│   ├── ExtrasService      随机事件、空投、天气/昼夜、猎人追踪
│   ├── ElytraStore        独立鞘翅槽与持久化
│   ├── SortService        服务端背包/箱子排序
│   └── LobbyService       大厅平台、传送、大厅物品
├── network/               棋盘状态、操作、排序、鞘翅同步
├── client/                HUD、棋盘详情页、大厅页、YACL 设置、按键
├── mixin/                 128 堆叠、维度访问、鞘翅槽、滑翔
└── data/bingo_ourselves/  物品/进度目标池、121 个任务进度
```

---

## 3. 命令

| 命令 | 说明 |
|------|------|
| `/bingo join <team>` / `/bingo leave` | 加入/离开队伍 |
| `/bingo ready` | 准备；全员准备后自动倒计时 |
| `/bingo start` / `/bingo start force` | 开始（force 跳过人数检查） |
| `/bingo end` / `/bingo lobby` | 结束本局 / 回到大厅 |
| `/bingo board` | 打开棋盘详情页（控制台显示摘要） |
| `/bingo reroll` | 重掷当前棋盘 |
| `/bingo size 5|7|9` | 棋盘尺寸 |
| `/bingo boards 1..4` | 棋盘数量 |
| `/bingo board <i> difficulty <...>` | 单个棋盘难度 |
| `/bingo difficulty easy|medium|hard|extreme|custom` | 全局难度 |
| `/bingo distribution <S> <A> <B> <C> <D>` | 自定义各难度格数 |
| `/bingo goal lines|items|cards <n>` | 胜负目标 |
| `/bingo time <分钟>` | 时间限制 |
| `/bingo lockout on|off` / `fog on|off` | 抢占 / 迷雾 |
| `/bingo hunter on|off` | 追杀模式 |
| `/bingo spawn <dimension>` | 设置下一局出生维度（overworld/the_nether/the_end） |
| `/bingo kit none|player|team` | 初始物品配置 |
| `/bingo chest` | 团队仓库 |
| `/bingo supply` / `/bingo event <type>` | 空投 / 随机事件 |
| `/bingo weather ...` / `/bingo daylight ...` | 固定天气 / 昼夜 |
| `/bingo preset classic|missions|mixed|speedrun|nether|end|hunter` | 预设 |
| `/bingo teams <2..8>` | 队伍数量 |
| `/bingo stack` | 验证 128 堆叠 |
| `/bingo info` / `/bingo reload` | 诊断 / 重载配置与目标数据 |

客户端按键：`Y` 棋盘详情、`H` 隐藏/显示 HUD、`O` 设置、`G` 大厅界面、`R` 整理背包/箱子、`M` 世界地图、`N` 小地图开关。

---

## 4. 构建与发行

```bash
./gradlew build                 # 构建 Mod
python3 tools/build_pack.py     # 下载固定版本工具 Mod，生成 mrpack / 服务端包 / mods 目录
python3 tools/smoke_test.py     # 服务端冒烟测试
```

产物：

| 文件 | 说明 |
|------|------|
| `dist/Bingo-26.3-1.0.0.mrpack` | Modrinth 整合包 |
| `dist/bingo-26.3-server.zip` | 解压即用服务端（含启动器、服务端 Mod、配置） |
| `dist/mods/` | 手动安装的 mods 目录 |

服务端：解压，阅读 EULA 并把 `eula.txt` 改为 `true`，运行 `./start.sh`；进入后 `/bingo join red`、`/bingo ready`，管理员 `/bingo start`。

---

## 5. 配置

`config/bingo-ourselves/server.json` 覆盖大厅、棋盘、对局、世界、扩展玩法；`client.json` 覆盖 HUD 与集成开关。`/bingo` 命令会即时写回服务端配置。

任务池：

- `data/bingo_ourselves/bingo/items.json`：1503 个物品目标（按 S/A/B/C/D 分档）
- `data/bingo_ourselves/bingo/advancements.json`：121 个进度任务（按档位）
- `data/bingo_ourselves/advancement/`：5 个标签页的进度树

可用 `python3 tools/generate_advancements.py` 与 `tools/generate_objectives.py` 从 26.3 原版数据重新生成（脚本注释中有输入说明）。

---

## 6. 已知限制

- 动态维度创建依赖服务端 API；极少数与服务端世界管理深度耦合的 Mod 可能不兼容。创建失败时会自动退回固定维度 + 随机区域。
- 独立鞘翅槽在玩家背包界面右侧（x=152, y=62）；原版客户端未安装本 Mod 时看不到该槽。
- 迷雾只对客户端隐藏；服务端仍会检测隐藏格的完成，避免“看不见但已完成”丢失判定。
- 追杀模式的平衡数值（生命、追踪、装备）可通过 `server.json` 调整。

## 7. 许可

本工程代码与数据：MIT。内置工具 Mod 版权归各自作者所有，按 Modrinth 页面许可分发。

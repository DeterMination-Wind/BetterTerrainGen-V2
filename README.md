# Better Terrain Gen V2 / 更自然的地形生成

## 中文

> 让自动生成的水域更像自然地貌，而不是规则涂块。

Better Terrain Gen V2 为 Mindustry 地图编辑器补充自然水体生成能力，让湖泊、海岸、浅滩和深水之间的过渡更自然。它适合希望快速得到可用地形、又不想在生成后反复手工修形的地图作者。

这个模组只服务于地图编辑和生成流程，不增加游戏内容，也不会改变已经开始的普通战斗。

### 需求与安装

从 Release 下载桌面与 Android 通用的 BetterTerrainGen-V2 JAR，放入 Mindustry 的 mods 目录并启用。

地图编辑器的“生成地形”中提供“遗迹生成”和“结构化遗迹生成”两个滤镜。Overlay 模式需要先在地图上放置过滤器中指定的 Overlay 标记；Auto 模式默认只处理陆地，中心密度为 25%，最小中心间距为 24 格，并由过滤器种子决定结果。遗迹步骤支持噪声筛选、地板/墙体/移除墙体/保留地板，以及几何、曼哈顿和切比雪夫距离。推荐先运行“自然水体”，再运行“遗迹生成”或“结构化遗迹生成”。

“遗迹生成”默认使用 `Scatter` 模式，以保持旧地图配置和小型遗迹的结果不变；“结构化遗迹生成”直接使用 `Structured` 模式。结构化滤镜采用从中心大厅沿房间边缘生长的 Digger 风格布局：少量一级房间形成主干道，其他房间从已有房间挂接，并保留少量环路。它会按种子生成带缺角和塌陷的房间、次级支路、断续外围墙、残墙和多种金属面板/受损地板；`Small`、`Medium`、`Large` 预设会按地图短边自适应，`Large` 的单个结构包络约占短边 80%，Auto 模式下只生成一个 Large 遗迹。结构模式只生成地板、静态墙和环境结构，不放置炮塔、传送带或其他可交互建筑。Auto 模式仍默认只处理陆地，并跳过液体和 synthetic 方块；Overlay 模式允许结构贴近地图边缘并裁剪到边界。

### 构建

~~~powershell
.\gradlew.bat releaseBuild
~~~

可使用 -PreleaseVersion=x.y.z 指定产物版本号。输出位于 dist/。

## English

> Make generated water look like a landscape instead of a paint bucket.

Better Terrain Gen V2 adds a natural-water workflow to the Mindustry map editor. It creates more convincing transitions between lakes, coastlines, shallows, and deep water, so map authors can start from a useful terrain shape instead of repairing every shoreline by hand.

The mod focuses on map generation and editing. It adds no gameplay content and does not affect an ordinary battle after the map is created.

### Requirements and install

Download the desktop-and-Android BetterTerrainGen-V2 JAR from Releases, put it in Mindustry's mods directory, and enable it.

The map editor's `Generate Terrain` dialog also provides `Ruin Generate` and `Structured Ruin Generate`. Overlay mode requires placing the selected overlay marker on the map first. Auto mode processes land by default, uses 25% center density and 24-tile minimum spacing, and is deterministic for the filter seed. Ruin steps support noise selection, floor/wall/remove-wall/preserve-floor operations, and geometric, Manhattan, or Chebyshev distance. For a natural result, run `Natural Water` before either ruin generator.

`Ruin Generate` defaults to `Scatter`, preserving existing map configurations and small-ruin behavior; `Structured Ruin Generate` is the standalone structured generator. Structured uses a Digger-style layout that grows from the center hall along room edges: a few first-level rooms form the main boulevard, later rooms attach to existing rooms, and a small number of loops are retained. It generates rooms with clipped corners and collapses, secondary branches, broken perimeter walls, detached debris walls, and varied metal panel and damaged-floor materials. `Small`, `Medium`, and `Large` adapt to the map's short side; one `Large` structure covers an envelope of about 80% of that side, and Structured Auto creates only one Large ruin. Structured mode generates environmental floors, static walls, and other scenery only: it does not place turrets, conveyors, or interactive buildings. Auto still targets land by default and skips liquid and synthetic blocks; Overlay structures may be clipped at map edges.

### Build

~~~powershell
.\gradlew.bat releaseBuild
~~~

Use -PreleaseVersion=x.y.z to set the artifact version. Outputs are written to dist/.

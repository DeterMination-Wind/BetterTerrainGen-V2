[中文](#中文) | [English](#english)

# 中文
<h1 align="center">
  <a href="https://github.com/DeterMination-Wind/BetterTerrainGen-V2/releases/latest"><img src="https://img.shields.io/github/v/release/DeterMination-Wind/BetterTerrainGen-V2?display_name=release&label=Latest%20Release&color=green"></a>
  <a href="https://github.com/DeterMination-Wind/BetterTerrainGen-V2/releases"><img src="https://img.shields.io/github/downloads/DeterMination-Wind/BetterTerrainGen-V2/total?label=Downloads&color=blue"></a>
  <a href="https://github.com/DeterMination-Wind/BetterTerrainGen-V2"><img src="https://img.shields.io/github/stars/DeterMination-Wind/BetterTerrainGen-V2?style=flat&label=Star%20this%20mod!&color=yellow"></a>
</h1>

## Better Terrain Gen V2

让地图编辑器里的水域更像自然形成的湖泊、海岸与浅滩。

### 特点

- 湖泊与海岸拥有自然的轮廓。
- 水域会从浅滩、浅水逐渐过渡到深水。
- 噪声扭曲的深度过渡（Depth Warp），让海岸线、湖岸线更加自然不规则。
- 可调节浅滩/浅水零散度，生成零散的天然小岛。
- 可自定义浅滩、浅水与深水使用的地板方块，并可用"自然化清理"开关一键移除孤立水块与小岛。
- 自动减少零散小水块和不自然的小岛。
- 保留地图中的建筑、核心与出生点。
- 支持随机种子和常用的水域调整选项。

### 需求

- Mindustry v159 及以上（桌面与 Android 均可，产物内置 `classes.dex`）。

### 构建

1. 需要 JDK 17。Android 打包步骤（D8）会从 `ANDROID_SDK_ROOT` / `ANDROID_HOME` / `D8_PATH` 自动查找。
2. 运行 `./gradlew releaseBuild`，可用 `-PreleaseVersion=x.y.z` 指定产物版本号。
3. 产物为 `dist/BetterTerrainGen-V2-<版本>.jar`，已内置 `mod.json` 与 `classes.dex`，桌面与 Android 通用。

### 使用

1. 下载本页面发布的 `.jar` 文件。
2. 将文件放入 Mindustry 的 `mods` 文件夹。
3. 重启游戏，在地图编辑器的“生成地形”中选择“自然水体”。

### 作者

DeterMination-Wind

---

# English

## Better Terrain Gen V2

Makes water in the Mindustry map editor feel more like naturally formed lakes, coastlines, and shallows.

### Features

- Natural-looking lake and coastline shapes.
- Smooth transitions from shoals to shallow and deep water.
- Noise-warped depth layers (Depth Warp) for more irregular, natural coastlines and lake shores.
- Shoal/shallow fragmentation sliders that create scattered natural islets.
- Custom floor blocks for shoals, shallow water, and deep water, plus a `Natural Cleanup` toggle to remove isolated water patches and islets.
- Fewer isolated water patches and unnatural islands.
- Buildings, cores, and spawn points are preserved.
- Supports random seeds and practical water-shaping options.

### Requirements

- Mindustry v159+ (desktop and Android; the artifact bundles `classes.dex`).

### Build

1. Requires JDK 17. The Android dex step (D8) is located automatically via `ANDROID_SDK_ROOT` / `ANDROID_HOME` / `D8_PATH`.
2. Run `./gradlew releaseBuild`, optionally with `-PreleaseVersion=x.y.z` to set the artifact version.
3. The output is `dist/BetterTerrainGen-V2-<version>.jar`, which already contains `mod.json` and `classes.dex` and works on both desktop and Android.

### Use

1. Download the `.jar` file from the release on this page.
2. Place it in Mindustry's `mods` folder.
3. Restart the game and choose `Natural Water` from `Generate Terrain` in the map editor.

### Author

DeterMination-Wind

# Box（TV影视）

> 基于 TVBox 开源生态的安卓电视盒子视频聚合播放器，支持点播、直播、投屏与多端推送，
> 采用「配置驱动」架构，通过外部接口地址即可加载各类影视/直播数据源，无需重新编译。

## 目录

- [功能特性](#功能特性)
- [环境要求](#环境要求)
- [快速开始](#快速开始)
  - [获取源码](#获取源码)
  - [一键构建](#一键构建)
  - [构建指定版本](#构建指定版本)
  - [签名说明](#签名说明)
  - [网络受限环境](#网络受限环境)
- [使用说明](#使用说明)
  - [配置数据源](#配置数据源)
  - [点播与搜索](#点播与搜索)
  - [直播与回看](#直播与回看)
  - [推送与投屏](#推送与投屏)
  - [网盘与本地播放](#网盘与本地播放)
- [定制默认参数](#定制默认参数)
- [项目结构](#项目结构)
- [技术栈](#技术栈)
- [持续集成](#持续集成)
- [发布与更新分发（维护者）](#发布与更新分发维护者)
- [常见问题](#常见问题)
- [免责声明](#免责声明)
- [开源许可](#开源许可)

## 功能特性

- **多内核数据源**：支持 Java Jar、JavaScript（QuickJS 引擎）与 Python（内置运行时）三种爬虫脚本，
  数据源由接口地址动态加载，可在设置中随时切换。
- **多播放器内核**：内置系统播放器、IJKPlayer、ExoPlayer（Media3）、阿里云播放器，
  并支持唤起 MXPlayer、Reex、Kodi 等第三方播放器，支持软/硬解码切换、画面缩放、倍速与多轨切换。
- **完善的点播体验**：聚合搜索、分类筛选、观看历史、收藏、豆瓣热播推荐、缩略图网格展示。
- **直播功能**：频道分组管理、EPG 节目单、台标 Logo（配置驱动，无需改代码）、自定义分组与频道备注。
- **推送与投屏**：内置 Web 服务（默认端口 `12345`），支持手机/电脑网页推送播放地址、扫码投屏、
  本地剪贴板内容直达详情页。
- **多端交互**：局域网遥控、网页控制台、分享与备份配置。
- **网盘与本地**：支持 WebDAV（含 Alist）、FTP、SMB/CIFS 等网盘协议，本地及远程字幕加载。
- **弹幕与字幕**：内置弹幕引擎（DanmakuFlameMaster），支持在线/本地/OCR 字幕。
- **体验细节**：多主题（奈飞、哆啦、百事、鸣人、小黄、八神、樱花共 7 套）、中英文切换、
  自定义字体（`/sdcard/tvbox.ttf`）、P2P 加速、后台播放与画中画。
- **多平台适配**：通过 Gradle Flavor 一键构建通用版、海信（Hisense）盒子定制版，支持
  `armeabi-v7a` 与 `arm64-v8a` 两种 ABI。

> 截图待补充。

## 环境要求

| 依赖 | 版本要求 |
| --- | --- |
| JDK | 17 及以上（AGP 8.8.2 要求） |
| Android SDK | compileSdk 35，minSdk 27（Android 8.1+） |
| Android Studio | Ladybug（2024.2.1）及以上，或直接使用命令行构建 |
| Gradle | 项目自带 `gradlew` 包装器（Gradle 8.11.1，腾讯镜像分发），无需手动安装 |

> 首次构建需联网下载依赖（已配置阿里云镜像与 JitPack 仓库，国内网络友好）。

## 快速开始

### 获取源码

```bash
git clone https://atomgit.com/cai_hj/Box.git
cd Box
```

### 一键构建

构建全部渠道包（release / debug）：

```bash
./gradlew assembleRelease    # 或 assembleDebug
```

- 构建产物位于 `app/build/outputs/apk/`，按变体分目录输出，命名格式：
  `TVBox_<buildType>-<flavorNames>.apk`，其中 `flavorNames` 由 `abi-brand-mode` 组合而成
  （`normal` 映射为 `java`），例如 `TVBox_release-arm64-generic-java.apk`、
  `TVBox_release-arm64-generic-python.apk`。
- 版本号随构建时间自动生成：`versionName` 为 `1.0.<yyyyMMdd_HHmm>`（北京时间），
  `versionCode` 为 `yyMMddHH`，见 `app/build.gradle` 的 `buildTime()`。

### 构建指定版本

项目包含三个 Flavor 维度，可按需组合：

| 维度 | Flavor | 说明 |
| --- | --- | --- |
| `abi` | `armeabi` / `arm64` | 仅打包对应 ABI 的 so 库（`armeabi` = armeabi-v7a，`arm64` = arm64-v8a） |
| `brand` | `generic` / `hisense` | 通用版 / 海信电视盒子定制版（海信版 applicationId 独立） |
| `mode` | `normal` / `python` | Java+JS 版 / 集成 Python 爬虫运行时版（两者 minSdk 均为 27） |

构建示例：

```bash
# 通用版 + arm64-v8a + Java/JS（最常用）
./gradlew assembleReleaseArm64GenericJava

# 通用版 + arm64-v8a + Python 数据源支持
./gradlew assembleReleaseArm64GenericPython

# 海信定制版 + arm64-v8a
./gradlew assembleReleaseArm64HisenseJava
```

完整变体列表可用 `./gradlew tasks --all | grep assemble` 查看。

### 签名说明

`release` 变体未内置签名配置，直接构建产物为未签名 APK。
正式分发前请在 `app/build.gradle` 的 `buildTypes.release` 中配置
`signingConfig`（签名文件不要提交到仓库）。

### 网络受限环境

- `build.gradle` 已配置 `mavenLocal()` 与项目内仓库 `local-maven/`，预置了
  因 JitPack 下载不稳定而无法拉取的构件（如 `nextlib-media3ext`），可直接离线使用；
- `gradle.properties` 的 JVM 参数已追加 `-Djava.net.preferIPv6Addresses=true`：
  本机 IPv4 到 jitpack.io 限速严重时（约 90KB/s），强制 IPv6 可恢复正常下载速度；
- `gradle.properties` 已启用 `org.gradle.parallel=true` 与 `org.gradle.caching=true`，
  多模块 / 多 flavor 构建显著提速。

## 使用说明

### 配置数据源

1. 打开应用，进入 **设置 → 配置地址**；
2. 粘贴仓库 / 接口地址（支持 jar、js、py 协议），保存后自动刷新；
3. 可在 **设置 → 历史接口** 中切换已保存的配置，或清除缓存重新加载。

> 接口地址格式约定：`jar://<url>`、`js://<url>`、`py://<url>` 或直接填写地址。
> 本项目不内置、不提供任何数据源地址，请自行准备合规的接口配置。

### 点播与搜索

- 首页支持 **推荐 / 历史 / 豆瓣热播** 三种模式（默认值可在 [`App.java`](#定制默认参数) 中调整）；
- 遥控器按搜索键或在首页进入搜索，支持全站聚合搜索与快速搜索选中条目；
- 内容详情页支持选集、多线路/多解析切换、收藏与历史记录。

### 直播与回看

- 在 **设置 → 直播地址** 中配置直播源（含分组与频道信息）；
- 支持 EPG 节目单（自定义 EPG 地址）、台标显示、频道排序与收藏；
- 若直播流无法播放，可在播放器中切换解码方式（软/硬解码）或更换播放内核。

### 推送与投屏

- 应用内置 Web 服务，打开 **设置 → 推送/遥控** 查看本机地址与二维码；
- 手机浏览器访问该地址即可搜索并推送视频到盒子播放；
- 支持复制了单集/详情页链接后直接冷启动跳转播放。

### 网盘与本地播放

- **设置 → 网盘** 支持 WebDAV（含 Alist）、FTP、SMB 等协议；
- 支持本地 U 盘 / 移动硬盘扫描，及局域网共享访问。

## 定制默认参数

应用默认参数定义在 `app/src/main/java/com/github/tvbox/osc/base/App.java` 的
`initParams()` 中（约 143 行起），节选如下：

```java
private void initParams() {
    putDefault(HawkConfig.HOME_REC, 1);        // 首页: 0=豆瓣热播, 1=推荐, 2=观看历史
    putDefault(HawkConfig.HOME_NUM, 4);        // 历史条数: 0=20条, 1=40条, 2=60条, 3=80条, 4=100条
    putDefault(HawkConfig.PLAY_TYPE, 1);       // 播放器: 0=系统, 1=IJK, 2=Exo, 3=阿里, 10=MX, 11=Reex, 12=Kodi
    putDefault(HawkConfig.IJK_CODEC, "硬解码"); // IJK 解码: 软解码 / 硬解码
    putDefault(HawkConfig.THEME_SELECT, 0);    // 主题: 0=奈飞, 1=哆啦, 2=百事, 3=鸣人, 4=小黄, 5=八神, 6=樱花
    putDefault(HawkConfig.SEARCH_VIEW, 1);     // 搜索展示: 0=文字列表, 1=缩略图
    putDefault(HawkConfig.DOH_URL, 0);         // 安全 DNS: 0=关闭, 1=腾讯, 2=阿里, 3=360, 4=Google, 5=AdGuard, 6=Quad9
    // ...
}
```

> 播放器取值以 `PlayerHelper.getPlayerName()`（`util/PlayerHelper.java`）为准；
> `App.java` 内同名注释已过时，勿以代码注释为准。

修改后重新构建即生效；用户首次运行后若已在设置中改过，将以此为准
（`Hawk` 键值已存在则不会覆盖）。

## 项目结构

```
Box/
├── app/                     # 主应用模块（Android）
│   ├── src/main/java/com/github/
│   │   ├── tvbox/osc/       # 应用主体：activity / ui / player / server / api / data 等
│   │   └── catvod/          # 爬虫框架：Spider 接口 / crawler / net / utils
│   ├── src/normal/          # Java 模式专用源码（pyLoader 等）
│   └── src/python/          # Python 模式专用源码/资源
├── quickjs/                 # QuickJS JavaScript 引擎模块（JS 脚本数据源）
├── pyramid/                 # Python 运行时模块（Python 脚本数据源）
├── xwalk/                   # Crosswalk WebView APK 存档（非 Gradle 模块，按需手动安装）
├── local-maven/             # 本地 Maven 仓库（预置 JitPack 不稳定构件）
├── build.gradle             # 顶层构建脚本（依赖与仓库镜像）
├── gradle/libs.versions.toml # 版本目录（统一声明依赖版本）
├── gradle.properties        # Gradle 全局配置（并行构建、缓存、IPv6 等）
└── settings.gradle          # 模块声明（root 工程名 TVBox，含 app/quickjs/pyramid）
```

## 技术栈

- **语言**：Java 8 + Kotlin 2.0.21（DataBinding）
- **构建**：Gradle 8.11.1（wrapper）、AGP 8.8.2、KSP 2.0.21-1.0.28、Chaquopy 12.0.1（Python）
- **UI**：AndroidX、RecyclerView 1.3.2（tv-recyclerview 3.0.0）、BaseRecyclerViewAdapterHelper 2.9.49、
  LoadSir 1.3.8、autosize 1.2.1
- **网络**：OkHttp 4.12.0（含 DoH）/ OkGo 3.0.4、Gson、jsoup 1.21.2、xstream 1.4.21、juniversalchardet 2.5.0
- **播放**：Media3 (ExoPlayer) 1.8.0、IJKPlayer、DKPlayer 3.3.7、阿里云播放器 5.5.2.0-full
  （含 alivc-conan 0.9.5 P2P 加速）
- **脚本引擎**：QuickJS（JS）、Chaquopy（Py）
- **存储**：Room 2.6.1（数据库，schema 位于 `app/schemas/`）、Hawk（键值存储）
- **服务**：AndServer 2.1.12（内置 Web/遥控）、NanoHTTPD 2.3.1（本地 HTTP 服务）
- **其他**：EventBus 3.3.1、Glide 4.16.0、XXPermissions、ZXing 3.5.3（二维码）、
  DanmakuFlameMaster 0.9.25（弹幕）、Sardine 0.8（WebDAV）

## 持续集成

`.github/workflows/test.yml` 提供 **Test Build** 工作流（`workflow_dispatch` 手动触发）：
拉起一次 `assembleRelease` 全量构建，并将产出的 APK 上传为 Artifact，用于验证工程可构建性。

## 发布与更新分发（维护者）

应用内「检查更新」从 Gitee 拉取，两个地址分离：

| 内容 | 位置 |
| --- | --- |
| 版本元数据 | Gitee 仓库 `raw/main/firmware/output-metadata.json` |
| APK 本体 | Gitee Release 附件 `releases/download/stable/` |

Gitee 对 raw 直链的大文件强制要求登录（匿名下载返回 403），因此 APK 必须使用 Release
附件分发。发布新版本时需同时：

1. 更新 `firmware/output-metadata.json` 中的 `versionName` 与 `outputFile`；
2. 将对应 APK 以相同文件名上传到 `stable` Release（同名附件重复上传会被加后缀，
   先删旧附件再上传）。

更新检查器已内置去重机制（`CHECK_IN_PROGRESS` 原子标志），避免重复排队；并按当前
构建变体（abi-brand-mode）自动匹配对应 APK 条目，匹配不到时退回首个条目。

## 常见问题

**Q：安装后提示"应用未安装"或"解析包错误"？**
本应用为盒子定制，明确各渠道 ABI（`armeabi` / `arm64`），请选择与设备 CPU 匹配的 APK；
同时请确认系统为 Android 8.1+（minSdk 27）。

**Q：某个源无法加载 / 播放失败？**
先确认该数据源地址在其他 TVBox 客户端是否可用；再尝试更换播放器内核与软/硬解码，
或关闭广告过滤与安全 DNS。

**Q：直播频道无画面 / 切台无效？**
直播无法播放多为直播源本身或解码方式导致：请确认直播源分组配置正确，
切换软/硬解码或更换播放内核后重试；台标 URL 现为配置驱动（`lives[].logo` 模板），
频道信息变更无需重新编译。

**Q：release 包无法安装 / 签名问题？**
release 未配置签名，需在 `app/build.gradle` 中自行添加 `signingConfig` 后重新构建。

**Q：应用内检查更新提示"下载失败"？**
APK 托管在 Gitee Release 附件，若下载失败多为网络原因或附件尚未发布；
发布侧的注意事项见 [发布与更新分发](#发布与更新分发维护者)。

**Q：Python 版为何比 Java 版体积大？**
`python` flavor 内置了 Python 运行时与依赖库，属于预期行为。

## 免责声明

本项目为开源学习与技术交流用途，仅提供服务框架与播放能力：

- 本项目 **不内置、不提供、不维护** 任何内容数据源，所有数据源由使用者自行配置并承担相应责任；
- 使用者应遵守所在地法律法规与版权规定，仅用于访问已获授权的合法内容；
- 使用本项目造成的任何影响，项目作者与贡献者不承担相关责任。

## 开源许可

本项目基于 [GNU AFFERO GENERAL PUBLIC LICENSE v3.0](./LICENSE) 开源发布，使用请遵循 AGPL-3.0 协议
（包括保留版权声明、开源衍生作品的源代码等义务）。部分组件采用各自的开源许可，详情见对应模块。

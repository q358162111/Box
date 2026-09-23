# Box（TV影视）

> 基于 TVBox 开源生态的安卓电视盒子视频聚合播放器，支持点播、直播、投屏与多端推送，
> 采用「配置驱动」架构，通过外部接口地址即可加载各类影视/直播数据源，无需重新编译。

## 目录

- [功能特性](#功能特性)
- [应用截图](#应用截图)
- [环境要求](#环境要求)
- [快速开始](#快速开始)
  - [一键构建](#一键构建)
  - [构建指定版本](#构建指定版本)
- [使用说明](#使用说明)
  - [配置数据源](#配置数据源)
  - [点播与搜索](#点播与搜索)
  - [直播与回看](#直播与回看)
  - [推送与投屏](#推送与投屏)
  - [网盘与本地播放](#网盘与本地播放)
- [默认设置修改](#默认设置修改)
- [项目结构](#项目结构)
- [技术栈](#技术栈)
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
- **体验细节**：多主题（奈飞、哆啦、百事、鸣人等）、中英文切换、自定义字体（`/sdcard/tvbox.ttf`）、
  安全 DNS（DoH：腾讯/阿里/360/Google 等）、P2P 加速、后台播放与画中画。
- **多平台适配**：通过 Gradle Flavor 一键构建通用版、海信（Hisense）盒子定制版，支持
  `armeabi-v7a` 与 `arm64-v8a` 两种 ABI。

## 应用截图

> 截图待补充。可在 `app/src/main/res` 中查看主题色与布局资源。

## 环境要求

| 依赖 | 版本要求 |
| --- | --- |
| JDK | 11 及以上（AGP 7.4.2 要求） |
| Android SDK | compileSdk 34，minSdk 21（Android 5.0+） |
| Android Studio | Giraffe（2022.3.1）及以上 |
| Gradle | 项目自带 `gradlew` 包装器，无需手动安装 |

> 首次构建需联网下载依赖（已配置阿里云镜像与 JitPack 仓库，国内网络友好）。

## 快速开始

```bash
git clone https://atomgit.com/cai_hj/Box.git
cd Box
```

### 一键构建

构建全部渠道包（release / debug）：

```bash
./gradlew assembleRelease    # 或 assembleDebug
```

构建产物位于 `app/build/outputs/apk/`，按变体分目录输出，命名格式：
`TVBox_<buildType>-<mode>-<abi>.apk`，例如 `TVBox_release-java-armeabi.apk`。

### 构建指定版本

项目包含三个 Flavor 维度，可按需组合：

| 维度 | Flavor | 说明 |
| --- | --- | --- |
| `abi` | `armeabi` / `arm64` | 仅打包对应 ABI 的 so 库 |
| `brand` | `generic` / `hisense` | 通用版 / 海信电视盒子定制版 |
| `mode` | `normal` / `python` | Java+JS 版 / 集成 Python 爬虫运行时版 |

构建示例：

```bash
# 通用版 + arm64-v8a + Java/JS（最常用）
./gradlew assembleGenericArm64JavaRelease

# 通用版 + arm64-v8a + Python 数据源支持
./gradlew assembleGenericArm64PythonRelease

# 海信定制版 + arm64-v8a
./gradlew assembleHisenseArm64JavaRelease
```

完整变体列表可用 `./gradlew tasks --all | grep assemble` 查看。

> 注意：`release` 变体未内置签名配置，直接构建产物为未签名 APK。
> 正式分发前请在 `app/build.gradle` 的 `buildTypes.release` 中配置
> `signingConfig`（签名文件不要提交到仓库）。

## 使用说明

### 配置数据源

1. 打开应用，进入 **设置 → 配置地址**；
2. 粘贴仓库 / 接口地址（支持 jar、js、py 协议），保存后自动刷新；
3. 可在 **设置 → 历史接口** 中切换已保存的配置，或清除缓存重新加载。

> 接口地址格式约定：`jar://<url>`、`js://<url>`、`py://<url>` 或直接填写地址。
> 本项目不内置、不提供任何数据源地址，请自行准备合规的接口配置。

### 点播与搜索

- 首页支持 **推荐 / 历史 / 豆瓣热播** 三种模式（可在 `App.java` 中调整默认值）；
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

## 默认设置修改

应用默认参数定义在 `app/src/main/java/com/github/tvbox/osc/base/App.java` 的 `initParams()` 中：

```java
private void initParams() {
    putDefault(HawkConfig.HOME_REC, 1);        // 首页: 0=豆瓣热播, 1=推荐, 2=观看历史
    putDefault(HawkConfig.PLAY_TYPE, 1);       // 播放器: 0=系统, 1=IJK, 2=Exo, 3=阿里, 10=MX, 11=Reex, 12=Kodi
    putDefault(HawkConfig.IJK_CODEC, "硬解码");  // IJK 解码: 软解码 / 硬解码
    putDefault(HawkConfig.THEME_SELECT, 0);    // 主题: 0=奈飞, 1=哆啦, 2=百事, 3=鸣人 ...
    putDefault(HawkConfig.SEARCH_VIEW, 1);     // 搜索展示: 0=文字列表, 1=缩略图
    putDefault(HawkConfig.DOH_URL, 0);         // 安全 DNS: 0=关闭, 1=腾讯, 2=阿里 ...
    // ...
}
```

修改后重新构建即生效；用户首次运行后若已在设置中改过，将以此为准（`Hawk` 键值已存在则不会覆盖）。

## 项目结构

```
Box/
├── app/                     # 主应用模块（Android）
│   ├── src/main/java/
│   │   └── com/github/
│   │       ├── tvbox/osc/   # 应用主体：activity / ui / player / server / api / data 等
│   │       └── catvod/      # 爬虫框架：Spider 接口 / Jar / JS / Python 加载器
│   │       └── google/      # 内置 ExoPlayer 相关扩展
│   └── src/normal/          # Java 模式专用源码（pyLoader 等）
│   └── src/python/          # Python 模式专用源码/资源
├── quickjs/                 # QuickJS JavaScript 引擎模块（JS 脚本数据源）
├── pyramid/                 # Python 运行时模块（Python 脚本数据源）
├── xwalk/                   # Crosswalk WebView WebKit 库
├── build.gradle             # 顶层构建脚本（依赖与仓库镜像）
├── gradle.properties        # Gradle 全局配置
└── settings.gradle          # 模块声明（root 工程名 TVBox）
```

## 技术栈

- **语言**：Java 8 + Kotlin（DataBinding）
- **UI**：AndroidX、RecyclerView（tv-recyclerview）、BaseRecyclerViewAdapterHelper、LoadSir、autosize
- **网络**：OkHttp / OkGo、Gson、jsoup、xstream、juniversalchardet
- **播放**：Media3 (ExoPlayer)、IJKPlayer、DKPlayer、阿里云播放器、P2P 加速
- **脚本引擎**：QuickJS（JS）、Chaquo Python（Py）
- **存储**：Room（数据库）、Hawk（键值存储）、multidex
- **服务**：AndServer（内置 Web/遥测）、NanoHttpd（WebSocket）
- **其他**：EventBus、Glide、XXPermissions、ZXing（二维码）、DanmakuFlameMaster（弹幕）、Sardine（WebDAV）

## 常见问题

**Q：安装后提示"应用未安装"或"解析包错误"？**
本应用为盒子定制，明确各渠道 ABI（`armeabi` / `arm64`），请选择与设备 CPU 匹配的 APK；
同时请确认系统为 Android 5.0+。

**Q：某个源无法加载 / 播放失败？**
先确认该数据源地址在其他 TVBox 客户端是否可用；再尝试更换播放器内核与软/硬解码，
或关闭广告过滤与安全 DNS。

**Q：直播频道无画面 / 切台无效？**
直播无法播放多为直播源本身或解码方式导致：请确认直播源分组配置正确，
切换软/硬解码或更换播放内核后重试；同时建议升级至最新版本（历史版本存在
CLEARTEXT 明文与主机名校验问题，会导致部分直播频道无法播放）。

**Q：release 包无法安装 / 签名问题？**
release 未配置签名，需在 `app/build.gradle` 中自行添加 `signingConfig` 后重新构建。

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
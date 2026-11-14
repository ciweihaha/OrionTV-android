# OrionTV (Kotlin 原生重写)

Android TV 播放器，Kotlin 原生重写版。由 React Native（Expo）版 OrionTV 重写而来，
与 mytv-android 同栈：Kotlin + Leanback + Media3（ExoPlayer）。

## 第一阶段（当前）

- 可编译的单模块工程：Gradle 8.7 + AGP 8.5.2 + Kotlin 2.0.21，
  minSdk 21 / targetSdk 34 / compileSdk 34
- 核心骨架：启动、首页（Leanback 浏览）、直播页、点播浏览、详情页、播放器
- 播放器：Media3 ExoPlayer，含跳过片头按钮（T5）与快进落点预览（T6/T7）
- 更新检查：继续走 okys-agg 的两个端点
  `/agg?ac=oriontv-version` 与 `/agg/oriontv-version.json`
- GitHub Actions：`assembleDebug`，CI 绿灯

**注意：第一阶段的频道、节目单、点播内容均为占位数据**（代码里以
`PlaceholderData` 标明，界面上有“演示数据”提示），源接入（TVBox 兼容源解析）
与真实 EPG 在第二阶段做，接口与模块位置已留好。

## 设计令牌（按已批准的改版预览）

- 深空底 `#0A0E1A`，面板 `#12141F`，星尘金焦点色 `#E8C15A`
- 字号整体降一级（区块标题 15sp、频道名 16sp、正文 15sp、小字 13sp）
- 全屏安全边距 48dp（约 3.4%），修 T9（首页/直播字太大、右侧“热门”被切边）
- 圆角 12dp

## 红线

点播首页、T2、BBC、天光云影、TV 5.9.0、凤凰这些来源/栏目不做任何改动；
moontv 不碰。T8（正片中赌博广告自动跳过）先判定嵌入方式，烧录在画面里就
做不到，本阶段不做。

## 本地构建

```bash
./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

需要 JDK 17 与 Android SDK（platform 34 + build-tools 34.0.0，
Gradle 会按需自动下载）。首次运行会下载 Gradle 8.7 发行包。

## 第二阶段（待做）

- TVBox 兼容源解析（`data/source/SourceRepository` 接口已留好）
- 真实 EPG 接入（`data/epg/EpgRepository` 接口已留好）
- 收藏、观看记录持久化、搜索、设置页
- T8 判定与断点续播

# OrionTV (Kotlin 原生重写)

Android TV 播放器，Kotlin 原生重写版。由 React Native（Expo）版 OrionTV 重写而来，
与 mytv-android 同栈：Kotlin + Leanback + Media3（ExoPlayer）。

## 当前状态（第 11 轮，2026-11-15：第 1 轮恢复）

- 可编译的单模块工程：Gradle 8.7 + AGP 8.5.2 + Kotlin 2.0.21，
  minSdk 21 / targetSdk 34 / compileSdk 34
- 核心骨架：启动、首页（Leanback 浏览）、直播页、点播浏览、详情页、播放器
- **直播**：M3U 播放列表解析（对齐 RN 语义）+ 两级缓存（内存 + 磁盘 7 天），
  设置页可配置 M3U 地址（默认 https://live.ciweihaha.win/live.m3u），
  播放器内可切换同名频道的多条线路（第 9 轮）
- **点播**：agg CMS 真实源（分类、浏览、详情、选集可播），点播浏览支持翻页
  （滚动到底自动加载下一页，第 9 轮）；未配置 TVBox 源时显示“未配置”空态，
  点播不设默认第三方源
- **TVBox 点播源（第 11 轮恢复）**：TvBoxParser + TvBoxSourceRepository，
  CMS JSON API（`?ac=list` / `?ac=videolist` / `?ac=detail`），
  分类/浏览/搜索/详情全通；未配置 TVBox 源地址时点播相关调用返回空，
  不影响 agg 源。搜索页、收藏页已接通，首页「应用」行有入口
- **EPG（第 11 轮恢复）**：XMLTV 解析器 + XmlTvEpgRepository，
  直播页 now/next 已接真实节目单（默认 https://ysp.ciweihaha.win/epg.xml，
  按 `tvg-id` 匹配，失败回落按台名匹配；拉取失败保留上次好的缓存）；
  首页直播行仍是占位数据
- **收藏与观看记录（第 11 轮恢复）**：LibraryStore（SharedPreferences），
  详情页可按「收藏」添加/取消，首页「应用」行进「我的收藏」；
  搜索历史本地保存；WatchRecord 带断点续播位计算（距开头 10 秒内或
  距结尾 60 秒内从头播）
- 播放器：Media3 ExoPlayer，含跳过片头按钮（T5，固定意图窗口）与快进落点预览（T6/T7）
- 更新检查：继续走 okys-agg 的两个端点
  `/agg?ac=oriontv-version` 与 `/agg/oriontv-version.json`
- GitHub Actions：`assembleDebug`（先跑单测），CI 绿灯

**注意：首页直播行仍是占位数据**（代码里以
`PlaceholderData` 标明，界面上有“演示数据”提示）。第 1 轮当初未推送的
17 个文件已在本轮全部恢复并补齐缺失的依赖（FavoriteItem/WatchRecord 模型、
SourceRepository.search()、Channel.tvgId、Manifest 注册），另接了
首页入口、详情页收藏按钮与 TVBox 详情兜底、直播页真实 EPG。

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
# 推荐：直接用 Gradle 8.7 发行版（不要用 ./gradlew，wrapper 会尝试下载发行包）
~/gradle-dist/gradle-8.7/bin/gradle testDebugUnitTest --max-workers=1
~/gradle-dist/gradle-8.7/bin/gradle assembleDebug --max-workers=1
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

需要 JDK 17 与 Android SDK（platform 34 + build-tools 34.0.0）。
`JAVA_TOOL_OPTIONS` 只加 `-Djava.net.preferIPv4Stack=true`，不要加代理。

## 还剩的缺口（下一轮候选）

- 首页直播行改走真实 M3U 源（目前仍是占位频道）
- T5 跳过片头真实检测（目前是固定意图窗口）；T8 判定；断点续播接播放器
- 真机检查（荣耀智慧屏 X1 LOK-360C，Android 9，API 28）

# Changelog

## 0.7.3

注意：本次更新需要重新构建数据库，建议备份数据库后再进行更新。 数据库迁移将自动执行，但仍建议备份。
注意：不可逆操作，建议备份数据库后再进行更新。
注意：部分前端环境变量已更名，请参考文档重新配置。

* 修复: KT引擎开启修复后时间分段失效的问题
* 修复: KT引擎开启修复后存在负数时间戳的问题
* 修复: KT引擎开启修复后，部分分段无法拖动时间轴的问题
* 修复: KT引擎开启修复后，视频、音频头参数更改不会触发分段的问题
* 修复: KT引擎开启修复后播放器遇到重复Metadata导致无法继续拖动FLV时间轴的问题
* 修复: 定时录制任务的时间计算错误的问题
* 修复: 部分主播录制失败的问题
* 修复: 上传任务未开始却显示正在上传的问题
* 修复: 部分抖音弹幕时间戳为0.0的问题
* 修复: 主播设置下载文件夹/文件名无法清空的问题
* 修复(虎牙): 星秀区录制失败的问题，默认使用WUP方法获取直播间信息
* 添加(KT引擎): 支持修复LEGACY HEVC(codec=12)编码
* 添加(KT引擎): FLV解析器添加重试机制，或许对网路波动有所帮助
* 添加: 支持录制微博平台
* 添加: 支持斗鱼平台弹幕颜色
* 添加: 支持单平台设置开播检查间隔
* 添加: 支持`{platform}` (平台名称) 占用符
* 添加: 解析器API，支持前端在线观看纯净直播流
* 添加(实验性）: 支持在线观看/下载录制视频（FLV格式暂时有bug，仅支持TS,MP4格式）
* 调整(Dockerfile): 使用`ykdl`修改后的`ffmpeg`引擎，支持国内不标准的HEVC编码
* 调整(数据库)!: 更改主播录制状态的存储方式
* 调整: 主播短时间下载失败后禁用检测n分钟（避免死循环）
* 调整: 最大分段大小和最大分段时间不再需要重启服务/录制任务
* 调整: 平台检测延迟逻辑重写，不再是实验性功能
* 调整: 平台录制逻辑重写，修复某些主播因某种奇怪的原因导致录不上的问题

- 调整: Twitch平台不再强制性需要auth token（可能不会录制到最高画质）

* 优化: 收到平台直播结束通知后，不再继续重试下载（限抖音）
* 优化: 优化下载流程的重试逻辑
* 文档: 使用Vitepress重构文档，[新文档站点](https://stream-rec.github.io/docs/zh_CN/)

## [0.7.2]

## What's Changed

* build(deps): dependencies updates
* feat(douyu-extractor): support huo3 CDN by @hua0512 in https://github.com/stream-rec/stream-rec/pull/162
* feat: flv avc/hls parser by @hua0512 in https://github.com/stream-rec/stream-rec/pull/159
* feat(server): config version api by @hua0512 in https://github.com/stream-rec/stream-rec/pull/165
* feat(server): add an api to manage streamer's activation state by @hua0512 in https://github.com/stream-rec/stream-rec/pull/166
* feat(streamlink-engine): add support for ttvlol plugin by @hua0512 in https://github.com/stream-rec/stream-rec/pull/167
* feat(douyin-danmu): add multiple websocket domains by @hua0512 in https://github.com/stream-rec/stream-rec/pull/168
* docs: add buy me a coffee by @hua0512 in https://github.com/stream-rec/stream-rec/pull/170
* docs: add afdian by @hua0512 in https://github.com/stream-rec/stream-rec/pull/171
* refactor(ffmpeg-engine): use ffprobe to detect resolution changes by @hua0512 in https://github.com/stream-rec/stream-rec/pull/180
* feat: add support for timer task downloads by @hua0512 in https://github.com/stream-rec/stream-rec/pull/185
* chore: prepare to bump to `0.7.2` ver by @hua0512 in https://github.com/stream-rec/stream-rec/pull/20

- ---------------------------------------

* 构建(deps): 更新依赖
* 增加(斗鱼): 支持火山CDN
* 增加: 新下载引擎，支持flv avc修复
* 增加(后端): 配置版本API
* 增加(后端): 管理主播激活状态的API
* 增加(streamlink): 支持ttvlol插件
* 增加(抖音): 添加多个websocket域名
* 文档: 添加爱发电链接
* 重构(ffmpeg): 使用ffprobe检测分辨率变化（实验性）
* 增加: 支持定时任务下载
* 添加: 下载引擎功能对比文档
* 修复: 部分主播未成功下播

**Full Changelog**: https://github.com/stream-rec/stream-rec/compare/v0.7.1...v0.7.2

## [0.7.1]

## What's Changed

* build(deps): bump com.google.devtools.ksp from 2.0.0-1.0.23 to 2.0.0-1.0.24 by @dependabot in https://github.com/hua0512/stream-rec/pull/113
* build(deps): bump protobuf from 4.27.2 to 4.27.3 by @dependabot in https://github.com/hua0512/stream-rec/pull/114
* build(deps): bump dagger from 2.51.1 to 2.52 by @dependabot in https://github.com/hua0512/stream-rec/pull/118
* build(deps): bump com.google.devtools.ksp from 2.0.0-1.0.24 to 2.0.10-1.0.24 by @dependabot in https://github.com/hua0512/stream-rec/pull/120
* build(deps): bump kotlin from 2.0.0 to 2.0.10 by @dependabot in https://github.com/hua0512/stream-rec/pull/119
* build(deps): bump androidx-room from 2.7.0-alpha05 to 2.7.0-alpha06 by @dependabot in https://github.com/hua0512/stream-rec/pull/122
* build(deps): bump androidx-sqlite from 2.5.0-alpha05 to 2.5.0-alpha06 by @dependabot in https://github.com/hua0512/stream-rec/pull/121
* feat(db): hash user password with md5 by @hua0512 in https://github.com/hua0512/stream-rec/pull/123
* build(deps): bump org.junit.jupiter:junit-jupiter from 5.10.3 to 5.11.0 by @dependabot in https://github.com/hua0512/stream-rec/pull/125
* build(deps): bump ch.qos.logback:logback-classic from 1.5.6 to 1.5.7 by @dependabot in https://github.com/hua0512/stream-rec/pull/126
* refactor(huya-danmu): use reversed-engineered danmu registration workflow by @hua0512 in https://github.com/hua0512/stream-rec/pull/127
* feat(douyin-extractor): add support for double screen streams by @hua0512 in https://github.com/hua0512/stream-rec/pull/128
* ci(docker): merge and enhance Docker workflows by @hua0512 in https://github.com/hua0512/stream-rec/pull/130
* build(deps): bump org.jetbrains.kotlinx:kotlinx-datetime from 0.6.0 to 0.6.1 by @dependabot in https://github.com/hua0512/stream-rec/pull/133
* build(deps): bump androidx-sqlite from 2.5.0-alpha06 to 2.5.0-alpha07 by @dependabot in https://github.com/hua0512/stream-rec/pull/134
* build(deps): bump androidx-room from 2.7.0-alpha06 to 2.7.0-alpha07 by @dependabot in https://github.com/hua0512/stream-rec/pull/135
* build(deps): bump com.google.devtools.ksp from 2.0.10-1.0.24 to 2.0.20-1.0.24 by @dependabot in https://github.com/hua0512/stream-rec/pull/136
* build(deps): bump kotlin from 2.0.10 to 2.0.20 by @dependabot in https://github.com/hua0512/stream-rec/pull/137
* build(deps): bump org.mockito:mockito-core from 5.12.0 to 5.13.0 by @dependabot in https://github.com/hua0512/stream-rec/pull/138
* build(deps): bump org.jetbrains.kotlinx:kotlinx-serialization-json from 1.7.1 to 1.7.2 by @dependabot
  in https://github.com/hua0512/stream-rec/pull/141
* build(deps): bump protobuf from 4.27.3 to 4.28.0 by @dependabot in https://github.com/hua0512/stream-rec/pull/139
* build(deps): bump org.jetbrains.kotlinx:kotlinx-serialization-protobuf from 1.7.1 to 1.7.2 by @dependabot
  in https://github.com/hua0512/stream-rec/pull/140
* feat(user): enhance user management and password recovery by @hua0512 in https://github.com/hua0512/stream-rec/pull/129
* fix(ffmpeg): move deletion of `core` file to proper location by @hua0512 in https://github.com/hua0512/stream-rec/pull/142
* feat(ActionService): add copy action by @hua0512 in https://github.com/hua0512/stream-rec/pull/143
* fix(pandatv-extractor): missing origin header by @hua0512 in https://github.com/hua0512/stream-rec/pull/144

- ---------------------------------------

* 构建(deps): 更新依赖
* 重构(用户): 优化重置密码流程
* 增加(数据库): 使用bcrypt加密用户密码
* 添加(抖音): 支持双屏直播
* 修复(FFMPEG): 自动删除`core`文件
* 添加(后处理): 添加复制后处理
* 修复(pandatv): 修复录制
* 重构(虎牙弹幕): 优化弹幕注册流程，使用官方流程

**Full Changelog**: https://github.com/hua0512/stream-rec/compare/v0.7.0...v0.7.1

## [0.7.0]

* build(Dockerfile): change to use `amazoncorretto:21-al2023-headless` as runtime image by @hua0512 in https://github.com/hua0512/stream-rec/pull/94
* chore(http): change to use OkHttp engine instead by @hua0512 in https://github.com/hua0512/stream-rec/pull/96
* refactor(download): manage exceptions properly by @hua0512 in https://github.com/hua0512/stream-rec/pull/97
* fix(download): suspend for streamer's cancel action by @hua0512 in https://github.com/hua0512/stream-rec/pull/100
* fix(danmu): extract xml-friendly string replacement into separat… by @hua0512 in https://github.com/hua0512/stream-rec/pull/102
* build(deps): bump androidx-sqlite from 2.5.0-alpha04 to 2.5.0-alpha05 by @dependabot in https://github.com/hua0512/stream-rec/pull/104
* build(deps): bump androidx-room from 2.7.0-alpha04 to 2.7.0-alpha05 by @dependabot in https://github.com/hua0512/stream-rec/pull/103
* build: support arm64 architecture by @hua0512 in https://github.com/hua0512/stream-rec/pull/107
* build(deps): bump com.google.devtools.ksp from 2.0.0-1.0.22 to 2.0.0-1.0.23 by @dependabot in https://github.com/hua0512/stream-rec/pull/106
* refactor(danmu): buffered write by @hua0512 in https://github.com/hua0512/stream-rec/pull/109
* refactor(code): change Douyin 'roomId' to 'webRid' by @hua0512 in https://github.com/hua0512/stream-rec/pull/110
* feat(backend): implement batch delete for stream and upload data by @hua0512 in https://github.com/hua0512/stream-rec/pull/111

- ---------------------------------------

* 构建(Dockerfile): 更改为使用`amazoncorretto:21-al2023-headless`作为运行时镜像
* 构建(Dockerfile): 支持arm64架构
* 重构(http): 更改为使用OkHttp引擎
* 修复(下载): 取消正在下载的任务后，无法再次启用下载
* 修复(弹幕): 替换弹幕发送者的名称为xml友好的字符串
* 修复(弹幕): 分段结束前写入缓冲区
* 修复(抖音): 支持'_' 直播间
* 增加(前/后端): 支持批量删除录播和上传日记

**Full Changelog**: https://github.com/hua0512/stream-rec/compare/v0.6.9...v0.7.0

## [0.6.9]

- refactor(danmu): use bilibili danmu format by @hua0512 in https://github.com/hua0512/stream-rec/pull/92
- feat(huya-extractor): add cookies validation
- chore(database): remove deprecated sqlDelight dependencies and migration

- ---------------------------------------

- 重构(弹幕): 使用bilibili弹幕格式
- 增加: 虎牙平台添加cookies验证
- 移除: 移除过时的sqlDelight依赖和迁移

**Full Changelog**: https://github.com/hua0512/stream-rec/compare/v0.6.8...v0.6.9

## [0.6.8]

- fix(douyin-danmu) : include signature parameter in request

- ---------------------------------------

- 修复: 斗音弹幕请求缺少签名参数

## [0.6.7]

- refactor(download): fallback to use synchronized handling
- feat(huya): add a hack for mobile API to use bitrates higher than 10000
- feat(upload): add support for retrying upload of failed files
- frontend: fix sidebar misbehavior

- ---------------------------------------

- 修复(下载): 回退到使用同步处理，解决部分下载问题
- 增加: 虎牙平台移动API支持高于10000码率的hack
- 添加: 前后端支持上传失败文件重试
- 修复: 前端侧边栏行为异常

## [0.6.6]

- Feat: add FFMPEG lossless segmentation -f segment (experimental)
- Feat: add FFMPEG exit on download error flag (experimental)
- Feat: add platform's fetch interval parameter (experimental)
- Feat: add Huya platform force original stream flag (experimental)
- Feat: add Huya platform use mobile API to fetch live room info
- Feat: add Twitch platform skips ads (experimental)
- Feat: add Twitch streamlink skip-ads parameter (experimental)
- Feat: add proxy settings (HTTP_PROXY environment variable, SOCKS proxy not supported)
- Optimize: Danmu will write 20 at a time to reduce IO write times
- Optimize: Replace SQLDelight with Jetpack Room
- Fix: skip Huya live HDR streams as unsupported
- Fix: Douyu quality selection
- Fix: Twitch Auth Token not set
- Fix: After the first segmentation, there is a probability that the Danmu cannot continue to be downloaded
- Adjust: after disabling streamer recording, post callbacks will be executed

- ---------------------------------------

- 增加: FFMPEG 无损分段 -f segment（实验性）
- 增加: FFMPEG 下载错误时退出（实验性）
- 增加: 平台轮询检查间隔参数 （实验性）
- 增加: 虎牙平台强制原画（实验性）
- 增加: 虎牙平台使用移动API获取直播间信息
- 增加: Twitch平台跳过广告（实验性）
- 增加: 代理设置（HTTP_PROXY环境变量，不支持SOCKS代理）
- 优化: 弹幕将一次性写入20条，减少IO写入次数
- 优化: 使用Jetpack Room 替换原有SQLDelight
- 修复: 虎牙直播跳过HDR选项
- 修复: 斗鱼直播间画质选择
- 修复: Twitch 直播间Auth Token未设置的问题
- 修复: 第一次分段后，有概率无法继续下载弹幕的问题

- 调整: 禁用主播录制后将执行回调



# Changelog

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



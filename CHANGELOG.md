# Changelog

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



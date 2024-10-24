<h4 align="right">
  <strong>简体中文</strong> | <a href="https://github.com/hua0512/stream-rec/blob/main/README.md">English</a>
</h4>

# Stream-rec

[![QQ交流群](https://img.shields.io/badge/QQ交流群-EB1923?logo=tencent-qq&logoColor=white)](https://qm.qq.com/q/qAbmjCuTug)
[![赞助](https://img.shields.io/badge/赞助-爱发电-ff69b4)](https://afdian.com/a/streamrec)

Stream-rec 是一个自动录制各种直播平台的工具。

基于 [Kotlin](https://kotlinlang.org/), [Ktor](https://ktor.io/), 和 [ffmpeg](https://ffmpeg.org/)。

本项目来源于我个人对一个能够自动录制直播,弹幕并支持分段上传到云存储的工具的需求。

> [!NOTE]\
> 本项目是我个人学习 Kotlin 协程、flow、Ktor、dao、repository 模式和其他技术的结果， 欢迎大家提出建议和意见。

# 功能列表

- 自动录播，可配置录制质量，路径，格式，并发量，分段录制（时间或文件大小），分段上传，根据直播标题和开始时间自动命名文件。
- 自动弹幕录制（XML格式），可使用 [DanmakuFactory](https://github.com/hihkm/DanmakuFactory) 进行弹幕转换，或配合[AList](https://alist.nn.ci/zh/)来实现弹幕自动挂载。
- 使用 [SQLite](https://www.sqlite.org/index.html) 持久化存储录播和上传信息
- 支持 [Rclone](https://rclone.org/) 上传到云存储
- 使用 Web 界面进行配置
- 支持 Docker
- 支持 FLV AVC 修复

# 直播平台支持列表

|    平台     | 录制 | 弹幕 |                                  链接格式                                   |
|:---------:|:--:|:--:|:-----------------------------------------------------------------------:|
|    抖音     | ✅  | ✅  |                  `https://www.live.douyin.com/{抖音id}`                   |
|    斗鱼     | ✅  | ✅  |                      `https://www.douyu.com/{直播间}`                      |
|    虎牙     | ✅  | ✅  |                      `https://www.huya.com/{直播间}`                       |
|  PandaTV  | ✅  | ✅  |              `https://www.pandalive.co.kr/live/play/{直播间}`              |
|  Twitch   | ✅  | ✅  |                      `https://www.twitch.tv/{直播间}`                      |
|    微博     | ✅  | ❌  | `https://weibo.com/u/{用户名}` 或 `https://weibo.com/l/wblive/p/show/{直播间}` |
| AfreecaTv | ❌  | ❌  |                                                                         |
| Bilibili  | ❌  | ❌  |                                                                         |
| Niconico  | ❌  | ❌  |                                                                         |
|  Youtube  | ❌  | ❌  |                                                                         |

- 更多平台的支持将在未来加入 (如果我有时间的话，欢迎PR)。

# 截图

![login.png](https://github.com/stream-rec/stream-rec-frontend/blob/master/docs/zh/login.png)
![dashboard.png](https://github.com/stream-rec/stream-rec-frontend/blob/master/docs/zh/dashboard.png)
![streamers.png](https://github.com/stream-rec/stream-rec-frontend/blob/master/docs/zh/streamers.png)

# 安装

# 1. Docker Compose(推荐)

## 1.1 构建 Docker compose 文件

请选择在一个无中文符号路径下创建一个 `docker-compose.yml` 文件，查看 [示例](example-docker-compose_zh.yml) 配置文件。

请详细阅读配置文件中的注释以获取更多信息，并确保修改关键部分，如密码、路径等。

## 1.2 运行 Docker compose

确保您在与 `docker-compose.yml` 文件相同的目录中，然后运行以下命令：

```shell
docker compose up -d && docker compose logs -f
```

稍等片刻，等待加载完成你就可以在 `http://localhost:15275` 访问 Web 界面并开始配置工具（参见 [配置](Configuration_zh.md)）。

> [!NOTE]\
> 您可以通过按 `Ctrl + C` 来退出日志。您可以通过运行 `docker compose logs -f` 来重新连接到日志。
> 如果您想停止服务，可以运行 `docker compose down`。

# 2. 从源码构建

## 2.1 环境要求

- 有魔法的网络（虽然但是，你都能上 GitHub 了，应该没问题）
- [Git](https://git-scm.com/downloads) (用于克隆仓库、后端获取服务器版本)
- Java 开发环境 (JDK) (版本 21 或更高),
  推荐使用 [Amazon Corretto 21](https://docs.aws.amazon.com/corretto/latest/corretto-21-ug/downloads-list.html)。
- [FFmpeg](https://ffmpeg.org/download.html) (确保它在你的系统变量 `PATH` 中)。 如果使用`kotlin` 引擎则不需要。
- [FFprobe](https://ffmpeg.org/download.html) (确保它在你的系统变量 `PATH` 中)。 开启`下载错误时退出`功能需要。
- [Streamlink](https://streamlink.github.io/install.html) (可选，用于录制，确保它在你的系统变量 `PATH` 中)
- [Rclone](https://rclone.org/downloads/) (可选，用于上传到云存储，确保它在你的系统变量 `PATH` 中)
- ~~[Sqlite3](https://www.sqlite.org/download.html) (用于存储录播和上传信息，确保它在你的系统变量 `PATH` 中)~~

## 2.2 构建后端服务

首先，克隆仓库并进入项目的根目录。

```shell
git clone https://github.com/hua0512/stream-rec.git
cd stream-rec
```

然后，使用以下命令构建项目：

```shell
./gradlew stream-rec:build -x test
```

构建的 fat jar 文件 `stream-rec.jar` 将位于 `stream-rec/build/libs` 目录中。

## 2.2.1 运行 jar 文件

使用以下命令运行 jar 文件：

```shell
java -jar stream-rec/build/libs/stream-rec.jar
```

可配置的环境变量如下：

- `DB_PATH`: 数据库文件路径 (默认: `./db`).
- `JWT_SECRET`: JWT 令牌生成的密钥.
- `LOG_LEVEL`: 日志级别 (默认: `info`).
- `LOGIN_SECRET`: Web 界面的登录密码 (默认: `stream-rec`)，只有在第一次运行时有效，后续修改不会生效。

例如：

```shell
java -DDB_PATH=/path/to/your/db -DLOG_LEVEL=DEBUG -DJWT_SECRET=SECRET -DLOGIN_SECRET=123 -jar stream-rec/build/libs/stream-rec.jar
```

## 2.3 构建前端服务

访问 [stream-rec-frontend ](https://github.com/hua0512/stream-rec-front) 仓库并按照说明构建前端服务。

完成后可以在 `http://localhost:15275` 访问 Web 界面并开始配置工具（参见 [配置](Configuration_zh.md)）。

# 故障排除

- 如果您遇到任何问题，请首先查看 [ISSUES](https://github.com/hua0512/stream-rec/issues)
- 工具默认会将日志输出到 `DB_PATH`，`logs` 目录中。
- 可以设置环境变量 `LOG_LEVEL` 为 `debug` 来启用调试日志。
- 如果您仍然遇到问题，请随时提出问题。

# 贡献

欢迎贡献！如果您有任何想法、建议或错误报告，请随时提出问题或拉取请求。

# 许可证

本项目根据 MIT 许可证进行许可。有关详细信息，请参阅 [LICENSE](../LICENSE) 文件。

# 感谢

- [Ktor](https://ktor.io/)
- [Kotlin](https://kotlinlang.org/)
- [FFmpeg](https://ffmpeg.org/)
- [Sqlite](https://www.sqlite.org/index.html)
- [Rclone](https://rclone.org/)
- [Streamlink](https://streamlink.github.io/)
- [ykdl](https://github.com/SeaHOH/ykdl)
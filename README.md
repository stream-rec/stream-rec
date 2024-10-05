<h4 align="right">
  <strong>English</strong> | <a href="https://github.com/hua0512/stream-rec/blob/main/docs/README_zh.md">简体中文</a>
</h4>

<div style="display: flex; align-items: center;">
  <h1 style="flex: 1;">Stream-rec</h1>
 <a href="https://www.buymeacoffee.com/hua0512"><img src="https://img.buymeacoffee.com/button-api/?text=Buy me a Coffee&emoji=🍘&slug=devvie&button_colour=FFDD00&font_colour=000000&font_family=Cookie&outline_colour=000000&coffee_colour=ffffff" height="40px" /></a>
</div>

Stream-rec is an automatic stream recording tool for various streaming services.

It's powered by Kotlin, [Ktor](https://ktor.io/), and [ffmpeg](https://ffmpeg.org/).

This project is the result of my personal need for a tool that can automatically record live streams and upload them to cloud storage.

> [!NOTE]\
> This project is the result of my personal learning of Kotlin Coroutines, flow, Ktor, dao, repository pattern and other technologies.

# Features

- Automatic stream recording, with configurable quality and format.
- Automatic file naming based on the stream title and start time.
- Automatic Danmu(Bullet comments) recording
- Persistent storage of stream and upload information (using SQLite)
- Integration with [Rclone](https://rclone.org/) for uploading to cloud storage
- Configurable via web interface.
- Docker support
- FLV AVC fix support

# Supported streaming services

|  Service  | Recording | Danmu |                   Url format                   |
|:---------:|:---------:|:-----:|:----------------------------------------------:|
|  Douyin   |     ✅     |   ✅   |    `https://www.live.douyin.com/{douyinId}`    |
|   Douyu   |     ✅     |   ✅   |         `https://www.douyu.com/{room}`         |
|   Huya    |     ✅     |   ✅   |         `https://www.huya.com/{room}`          |
|  PandaTV  |     ✅     |   ✅   | `https://www.pandalive.co.kr/live/play/{room}` |
|  Twitch   |     ✅     |   ✅   |         `https://www.twitch.tv/{room}`         |
| AfreecaTv |     ❌     |   ❌   |                                                |
| Bilibili  |     ❌     |   ❌   |                                                |
| Niconico  |     ❌     |   ❌   |                                                |
|  Youtube  |     ❌     |   ❌   |                                                |

- More services will be supported in the future (if I have time, PRs are welcomed).

# Screenshots

![login.png](https://github.com/stream-rec/stream-rec-frontend/blob/master/docs/en/login.png)
![dashboard.png](https://github.com/stream-rec/stream-rec-frontend/blob/master/docs/en/dashboard.png)
![streamers.png](https://github.com/stream-rec/stream-rec-frontend/blob/master/docs/en/streamers.png)

# Installation

# 1. Docker Compose (Recommended)

## 1.1 Configuring the docker-compose.yml file

Create a `docker-compose.yml`, take a look at the [example](docs/example-docker-compose.yml) configuration file.

Please read the comments in the configuration file for more information and make sure to modify crucial parts like passwords, paths, etc.

## 1.2 Running the Docker compose

Make sure you are in the same directory as the `docker-compose.yml` file, then run the following command:

```shell
docker compose up -d && docker compose logs -f
```

Now, you are all set! You can access the web interface at `http://localhost:15275` and start configuring the tool (
see [Configuration](docs/Configuration.md)).

> [!NOTE]\
> You can detach from the logs by pressing `Ctrl + C`. And you can reattach to the logs by running `docker compose logs -f`.
> To stop the containers, run `docker compose down`.

# 2. Building from source

## 2.1 Prerequisites

- Internet access, obviously 😂
- [Git](https://git-scm.com/downloads) (Used to get the version information by the backend)
- A java development kit (JDK) (version 21 or
  later), [Amazon Corretto 21](https://docs.aws.amazon.com/corretto/latest/corretto-21-ug/downloads-list.html) is recommended.
- [FFmpeg](https://ffmpeg.org/download.html) (Make sure it's in your `PATH`). No longer required if you are using the `kotlin` engine.
- [FFprobe](https://ffmpeg.org/download.html) (Make sure it's in your `PATH`). Required if `Exit on download error` is enabled.
- [Streamlink](https://streamlink.github.io/install.html) (optional, for recording streams, make sure it's in your `PATH`)
- [Rclone](https://rclone.org/downloads/) (optional, for uploading to cloud storage, make sure it's in your `PATH`)
- ~~[Sqlite3](https://www.sqlite.org/download.html) (for storing stream, upload information, make sure it's in your `PATH`)~~

## 2.2 Building the backend

To build the project, first clone the repository and navigate to the root directory of the project.

```shell
git clone https://github.com/hua0512/stream-rec.git
cd stream-rec
```

Then, build the project using the following command:

```shell
./gradlew stream-rec:build -x test
```

The built fat jar file `stream-rec.jar` will be located in the `stream-rec/build/libs` directory.

## 2.2.1 Running the jar file

To run the jar file, use the following command:

```shell
java -jar stream-rec/build/libs/stream-rec.jar
```

Several environment variables can be set to configure the tool:

- `DB_PATH`: Path to the SQLite database folder. (default: `./db`)
- `JWT_SECRET`: Secret key for JWT token generation.
- `LOG_LEVEL`: Log level (default: `info`).
- `LOGIN_SECRET`: Login password for the web interface (default: `stream-rec`, if not set).

For example:

```shell
java -DDB_PATH=/path/to/your/db -DLOG_LEVEL=DEBUG -DJWT_SECRET=SECRET -DLOGIN_SECRET=123 -jar stream-rec/build/libs/stream-rec.jar
```

> [!IMPORTANT]\
> Things to note:
> - Please set the `LOGIN_SECRET` environment variable to a secure password. This password is used to log in to the web interface.
> - This password cannot be changed after the first run.

## 2.3 Building the frontend

Frontend is used to configure the tool, it's a simple web interface built with React.

Navigate to [frontend](https://github.com/hua0512/stream-rec-front) repository and follow the build instructions.

After that, you can start configuring the tool by accessing the web interface at `http://localhost:15275`. Take a look at
the [Configuration](docs/Configuration.md) page for more information.

# Troubleshooting

Check logs under `logs` directory, by default, it will be created in the same directory as the `DB_PATH`.

- There´s a environment variable `LOG_LEVEL` that can be set to `debug` to enable debug logs.

# Contributing

Contributions are welcome! If you have any ideas, suggestions, or bug reports, please feel free to open an issue or a
pull request.

# License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.

# Credits

- [Ktor](https://ktor.io/)
- [Kotlin](https://kotlinlang.org/)
- [FFmpeg](https://ffmpeg.org/)
- [Sqlite](https://www.sqlite.org/index.html)
- [Rclone](https://rclone.org/)
- [Streamlink](https://streamlink.github.io/)
- [ykdl](https://github.com/SeaHOH/ykdl)

## Star History

<a href="https://star-history.com/#hua0512/stream-rec&Date">
 <picture>
   <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/svg?repos=hua0512/stream-rec&type=Date&theme=dark" />
   <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/svg?repos=hua0512/stream-rec&type=Date" />
   <img alt="Star History Chart" src="https://api.star-history.com/svg?repos=hua0512/stream-rec&type=Date" />
 </picture>
</a>
<h4 align="right">
  <strong>English</strong> | <a href="https://github.com/hua0512/stream-rec/blob/main/docs/README_zh.md">简体中文</a>
</h4>

<div style="display: flex; align-items: center;">
  <h1 style="flex: 1;">Stream-rec</h1>
 <a href="https://www.buymeacoffee.com/hua0512"><img src="https://img.buymeacoffee.com/button-api/?text=Buy me a Coffee&emoji=🍘&slug=devvie&button_colour=FFDD00&font_colour=000000&font_family=Cookie&outline_colour=000000&coffee_colour=ffffff" height="40px" /></a>
</div>

Stream-rec is an automatic stream recording tool for various streaming services.

It's powered by Kotlin, [Ktor](https://ktor.io/), and [ffmpeg](https://ffmpeg.org/).

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

|  Service  | Recording | Danmu |                                 Url format                                 |
|:---------:|:---------:|:-----:|:--------------------------------------------------------------------------:|
|  Douyin   |     ✅     |   ✅   |                    `https://live.douyin.com/{douyinId}`                    |
|   Douyu   |     ✅     |   ✅   |                       `https://www.douyu.com/{room}`                       |
|   Huya    |     ✅     |   ✅   |                       `https://www.huya.com/{room}`                        |
|  PandaTV  |     ✅     |   ✅   |               `https://www.pandalive.co.kr/live/play/{room}`               |
|  Twitch   |     ✅     |   ✅   |                       `https://www.twitch.tv/{room}`                       |
|   Weibo   |     ✅     |   ❌   | `https://weibo.com/u/{uid}` or  `https://weibo.com/l/wblive/p/show/{room}` |     
| AfreecaTv |     ❌     |   ❌   |                                                                            |
| Bilibili  |     ❌     |   ❌   |                                                                            |
| Niconico  |     ❌     |   ❌   |                                                                            |
|  Youtube  |     ❌     |   ❌   |                                                                            |

- More services will be supported in the future (if I have time, PRs are welcomed).

# Screenshots

![login.png](https://github.com/stream-rec/stream-rec-frontend/blob/master/docs/en/login.png)
![dashboard.png](https://github.com/stream-rec/stream-rec-frontend/blob/master/docs/en/dashboard.png)
![streamers.png](https://github.com/stream-rec/stream-rec-frontend/blob/master/docs/en/streamers.png)

# Documentation

Please refer to the [documentation](https://stream-rec.github.io/docs/) for more information.

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

<a href="https://star-history.com/#stream-rec/stream-rec&Date">
 <picture>
   <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/svg?repos=stream-rec/stream-rec&type=Date&theme=dark" />
   <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/svg?repos=stream-rec/stream-rec&type=Date" />
   <img alt="Star History Chart" src="https://api.star-history.com/svg?repos=stream-rec/stream-rec&type=Date" />
 </picture>
</a>
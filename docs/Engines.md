# 1. Download Engine Feature Support List

|                    Feature                    |                 Kotlin                  |                   FFMPEG                   |                STREAMLINK                |
|:---------------------------------------------:|:---------------------------------------:|:------------------------------------------:|:----------------------------------------:|
|                 FLV Download                  |                    ✅                    |                     ✅                      |                    ❌                     |
|                 HLS Download                  |         ✅ <br/>(Multithreaded)          |                     ✅                      |          ✅ <br/>(Multithreaded)          |
|           Recording Duration Stats            | ✅    <br/>(Raw data mode not supported) |                     ✅                      |                    ✅                     |
|            Download Bitrate Stats             |                    ✅                    |  ✅   <br/>(-f segmentation not supported)  | ✅   <br/>(-f segmentation not supported) |
|               Size Segmentation               |  ✅ <br/>(Raw data mode not supported)   | ✅     <br/>(-f segmentation not supported) |                    ✅                     |
|             Duration Segmentation             |  ✅  <br/>(Raw data mode not supported)  |                     ✅                      |                    ✅                     |
| Choose Between Size and Duration Segmentation |                    ❌                    |                     ✅                      |                    ✅                     |
|                Download Format                |            FLV, M3U8,TS, M4S            |           Supports other formats           |          Supports other formats          |
|                FLV AVC Repair                 |                    ✅                    |                     ❌                      |                    ❌                     |
|                   CPU Usage                   |                 Medium                  |                    Low                     |                   Low                    |
|                 Memory Usage                  |                 Medium                  |                    Low                     |                   Low                    |

# 2. FLV AVC Repair Feature List

|                          Feature                           | Engine Action   |
|:----------------------------------------------------------:|-----------------|
|                      Timestamp Jumps                       | Fix using delta |
|    Video Header Changes (Resolution, Other Parameters)     | Split file      |
|                    Audio Header Changes                    | Split file      |
| AMF Metadata Injection (lastheadertimestamp, keyframes...) | Inject          |
|                       Duplicate TAG                        | Ignore          |
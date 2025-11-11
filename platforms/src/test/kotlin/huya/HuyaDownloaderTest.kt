/*
 * MIT License
 *
 * Stream-rec  https://github.com/hua0512/stream-rec
 *
 * Copyright (c) 2025 hua0512 (https://github.com/hua0512)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package huya

import BaseDownloaderTest
import com.github.michaelbull.result.getError
import com.github.michaelbull.result.unwrap
import github.hua0512.data.config.DownloadConfig
import github.hua0512.data.media.MediaInfo
import github.hua0512.data.media.VideoFormat
import github.hua0512.data.stream.Streamer
import github.hua0512.plugins.base.ExtractorError
import github.hua0512.plugins.download.base.PlatformDownloader
import github.hua0512.plugins.huya.danmu.HuyaDanmu
import github.hua0512.plugins.huya.download.Huya
import github.hua0512.plugins.huya.download.HuyaExtractor
import io.kotest.matchers.shouldBe

/**
 * @author hua0512
 * @date : 2024/12/6 14:23
 */
class HuyaDownloaderTest : BaseDownloaderTest<HuyaExtractor, DownloadConfig.HuyaDownloadConfig>({


  val localStreamInfos by lazy {
    val text = this.javaClass.getResourceAsStream("/huya/huya_pc_live.json")!!.bufferedReader().readText()
    app.json.decodeFromString<MediaInfo>(text)
  }

  test("downloaderInitResult") {
    val result = downloader.init(streamer)
    result.isOk shouldBe true
  }

  test("downloaderLiveResult") {
    val result = downloader.init(streamer)
    result.isOk shouldBe true

    val liveResult = downloader.shouldDownload()
    liveResult.isOk shouldBe true
  }

  test("downloaderDefaultNoFilterApplied") {
    streamer = streamer.copy(downloadConfig = DownloadConfig.HuyaDownloadConfig())
    val result = downloader.init(streamer)
    result.isOk shouldBe true

    val filterResult = downloader.applyFilters(localStreamInfos.streams)
    filterResult.isOk shouldBe true
    val mediaInfo = filterResult.unwrap()
    mediaInfo.format shouldBe VideoFormat.flv
    mediaInfo.extras["cdn"] shouldBe "AL"
    mediaInfo.bitrate shouldBe 8000
    mediaInfo.quality shouldBe "蓝光8M"
  }

  test("downloaderCdnFilterApplied") {
    streamer = streamer.copy(downloadConfig = DownloadConfig.HuyaDownloadConfig(primaryCdn = "HW"))
    val result = downloader.init(streamer)
    result.isOk shouldBe true

    val filterResult = downloader.applyFilters(localStreamInfos.streams)
    filterResult.isOk shouldBe true
    val mediaInfo = filterResult.unwrap()
    mediaInfo.format shouldBe VideoFormat.flv
    mediaInfo.extras["cdn"] shouldBe "HW"
    mediaInfo.bitrate shouldBe 8000
    mediaInfo.quality shouldBe "蓝光8M"
  }

  test("downloaderMaxBitrateFilterApplied") {
    streamer = streamer.copy(downloadConfig = DownloadConfig.HuyaDownloadConfig().apply {
      maxBitRate = 1000
    })
    val result = downloader.init(streamer)
    result.isOk shouldBe true

    val filterResult = downloader.applyFilters(localStreamInfos.streams)
    filterResult.isOk shouldBe true
    val mediaInfo = filterResult.unwrap()
    mediaInfo.format shouldBe VideoFormat.flv
    mediaInfo.extras["cdn"] shouldBe "AL"
    mediaInfo.bitrate shouldBe 500
    mediaInfo.quality shouldBe "流畅"
  }

  test("downloaderMaxBitrateAndCdnFilterApplied") {
    streamer = streamer.copy(downloadConfig = DownloadConfig.HuyaDownloadConfig(primaryCdn = "HW").apply {
      maxBitRate = 8000
    })
    val result = downloader.init(streamer)
    result.isOk shouldBe true

    val filterResult = downloader.applyFilters(localStreamInfos.streams)
    filterResult.isOk shouldBe true
    val mediaInfo = filterResult.unwrap()
    mediaInfo.format shouldBe VideoFormat.flv
    mediaInfo.extras["cdn"] shouldBe "HW"
    mediaInfo.bitrate shouldBe 8000
    mediaInfo.quality shouldBe "蓝光8M"
  }

  test("downloaderSourceFormatFilterApplied") {
    streamer = streamer.copy(downloadConfig = DownloadConfig.HuyaDownloadConfig(sourceFormat = VideoFormat.hls).apply {

    })
    val result = downloader.init(streamer)
    result.isOk shouldBe true

    val filterResult = downloader.applyFilters(localStreamInfos.streams)
    filterResult.isOk shouldBe true
    val mediaInfo = filterResult.unwrap()
    mediaInfo.format shouldBe VideoFormat.hls
    mediaInfo.extras["cdn"] shouldBe "AL"
    mediaInfo.bitrate shouldBe 8000
    mediaInfo.quality shouldBe "蓝光8M"
  }

  // ensures that the downloader is able to handle the case when the primary CDN is not available, by falling back to the highest priority CDN available
  test("downloaderPrimaryCdnNotAvailableFilter") {
    streamer = streamer.copy(downloadConfig = DownloadConfig.HuyaDownloadConfig(primaryCdn = "HW").apply {
      maxBitRate = 8000
    })
    val result = downloader.init(streamer)
    result.isOk shouldBe true

    val streams = localStreamInfos.streams.toMutableList()
    streams.removeIf { it.extras["cdn"] == "HW" }
    streams.forEachIndexed { index, streamInfo ->
      val cdn = streamInfo.extras["cdn"]
      if (cdn == "TX") {
        streams[index] = streamInfo.copy(priority = 300)
      }
    }

    val filterResult = downloader.applyFilters(streams)
    filterResult.isOk shouldBe true
    val mediaInfo = filterResult.unwrap()
    mediaInfo.format shouldBe VideoFormat.flv
    mediaInfo.extras["cdn"] shouldBe "TX"
    mediaInfo.bitrate shouldBe 8000
    mediaInfo.quality shouldBe "蓝光8M"
  }

  test("downloaderFlvPrioritization") {
    streamer = streamer.copy(downloadConfig = DownloadConfig.HuyaDownloadConfig(sourceFormat = VideoFormat.hls).apply {
      maxBitRate = 8000
    })
    val result = downloader.init(streamer)
    result.isOk shouldBe true

    val streams = localStreamInfos.streams.toMutableList()
    streams.removeIf { it.format == VideoFormat.hls }

    val filterResult = downloader.applyFilters(streams)
    filterResult.isOk shouldBe true
    val mediaInfo = filterResult.unwrap()
    mediaInfo.format shouldBe VideoFormat.flv
    mediaInfo.extras["cdn"] shouldBe "AL"
    mediaInfo.bitrate shouldBe 8000
    mediaInfo.quality shouldBe "蓝光8M"
  }

  test("downloaderNoStreamsAvailable") {
    streamer = streamer.copy(downloadConfig = DownloadConfig.HuyaDownloadConfig(sourceFormat = VideoFormat.hls).apply {
      maxBitRate = 8000
    })
    val result = downloader.init(streamer)
    result.isOk shouldBe true

    val streams = localStreamInfos.streams.toMutableList()
    streams.clear()

    val filterResult = downloader.applyFilters(streams)
    filterResult.isErr shouldBe true
    filterResult.getError() shouldBe ExtractorError.NoStreamsFound
  }

}) {


  override fun createDownloader(): PlatformDownloader<DownloadConfig.HuyaDownloadConfig> {
    return Huya(app, HuyaDanmu(app), createExtractor())
  }

  override val testUrl: String = "https://www.huya.com/loljiezou"

  override var streamer: Streamer = Streamer(id = 1L, name = "huya", url = testUrl, downloadConfig = DownloadConfig.HuyaDownloadConfig())


  private fun getPCExtractor(url: String = testUrl) = HuyaExtractor(app.client, app.json, url).apply {
    prepare()
  }

  override fun createExtractor(url: String): HuyaExtractor = getPCExtractor(url)

}
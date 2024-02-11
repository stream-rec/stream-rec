package github.hua0512.plugins.base

import github.hua0512.app.App
import github.hua0512.data.StreamData
import github.hua0512.data.Streamer
import github.hua0512.data.StreamingPlatform
import github.hua0512.utils.deleteFile
import github.hua0512.utils.rename
import github.hua0512.utils.toLocalDateTime
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withPermit
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter.ofPattern
import kotlin.coroutines.resume
import kotlin.io.path.Path
import kotlin.time.DurationUnit
import kotlin.time.toDuration

abstract class Download(val app: App, val danmu: Danmu) {

  companion object {
    @JvmStatic
    protected val logger: Logger = LoggerFactory.getLogger(this::class.java)

    @JvmStatic
    protected val commonHeaders = arrayOf(
      HttpHeaders.Accept to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
      HttpHeaders.AcceptLanguage to "zh-CN,zh;q=0.8,en-US;q=0.5,en;q=0.3",
      HttpHeaders.UserAgent to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/108.0.3029.110 Safari/537.36"
    )

  }

  // downloadUrl is the url of the stream to be downloaded
  protected lateinit var downloadUrl: String

  // downloadTitle is the title of the stream to be downloaded
  protected var downloadTitle: String = ""
    set(value) {
      field = formatToFriendlyFileName(value)
    }

  // current attached streamer
  protected lateinit var streamer: Streamer

  /**
   * The regex pattern to be used to match the streamer url
   */
  abstract val regexPattern: String


  /**
   * Check if the stream should be downloaded
   * @param streamer the streamer to be checked
   * @return true if the stream should be downloaded, false otherwise
   */
  abstract suspend fun shouldDownload(streamer: Streamer): Boolean


  /**
   * Download the stream
   * @return a list of [StreamData] containing the downloaded stream data
   */
  suspend fun download(): List<StreamData> = coroutineScope {
    logger.info("Inside coroutine scope : $coroutineContext")
    // check if downloadUrl is valid
    if (downloadUrl.isEmpty()) return@coroutineScope emptyList()

    // download config is required and its should not be null
    val downloadConfig = streamer.downloadConfig ?: run {
      logger.error("(${streamer.name}) download config is required")
      return@coroutineScope emptyList()
    }

    val segmentPart = app.config.maxPartSize.run {
      if (this > 0) this else 2621440000
    }

    val defaultFFmpegOutputArgs = arrayOf(
      "-bsf:a",
      "aac_adtstoasc",
      "-fs",
      segmentPart.toString(),
    )

    val fileExtension = (downloadConfig.outputFileExtension ?: app.config.outputFileFormat).run { "${extension}.part" }

    val cookie = downloadConfig.cookies ?: when (streamer.platform) {
      StreamingPlatform.HUYA -> app.config.huyaConfig.cookies
      StreamingPlatform.DOUYIN -> app.config.douyinConfig.cookies
      else -> null
    }

    // ffmpeg input args
    val defaultFFmpegInputArgs = buildDefaultFFMpegInputArgs(cookie)
    // ffmpeg running commands
    val cmds = buildFFMpegRunningCmd(defaultFFmpegInputArgs, defaultFFmpegOutputArgs, fileExtension)

    // download the stream in parts
    // semaphore is used to limit the number of concurrent downloads
    app.downloadSemaphore.withPermit {
      withContext(Dispatchers.IO) {
        val streamDataList = mutableListOf<StreamData>()

        while (true) {
          logger.debug("Inside download loop : {}", coroutineContext)
          val localeDate = LocalDateTime.now().run {
            format(ofPattern("yyyy-MM-dd HH-mm-ss"))
          }

          // replace placeholders in the output file name
          val toReplace = mapOf(
            "{streamer}" to streamer.name,
            "{title}" to downloadTitle,
            "%yyyy" to localeDate.substring(0, 4),
            "%MM" to localeDate.substring(5, 7),
            "%dd" to localeDate.substring(8, 10),
            "%HH" to localeDate.substring(11, 13),
            "%mm" to localeDate.substring(14, 16),
            "%ss" to localeDate.substring(17, 19),
          )
          val outputFileName = (if (downloadConfig.outputFileName.isNullOrEmpty()) {
            app.config.outputFileName
          } else {
            downloadConfig.outputFileName!!
          }).run {
            formatToFriendlyFileName(toReplace.entries.fold(this) { acc, entry ->
              acc.replace(entry.key, entry.value)
            } + ".$fileExtension")
          }

          val outputFolder = (if (downloadConfig.outputFolder.isNullOrEmpty()) {
            app.config.outputFolder
          } else {
            downloadConfig.outputFolder!!
          }).run {
            val str = if (endsWith(File.separator)) this else this + File.separator
            // system file separator
            toReplace.entries.fold(str) { acc, entry ->
              acc.replace(entry.key, entry.value)
            }
          }

          val outputPath = Path(outputFolder + outputFileName).also {
            Files.createDirectories(it.parent)
            Files.exists(it).let { exists ->
              if (exists) {
                logger.error("(${streamer.name}) file already exists: $it")
                throw IllegalStateException("File already exists")
              }
            }
          }

          // check if disk space is enough
          checkDiskSpace(outputPath.parent, segmentPart)

          var streamData = StreamData(
            streamerId = 0,
            // FIXME : streamerId = streamer.id,
            // streamerId = streamer.id.referee,
            title = downloadTitle,
            outputFilePath = outputPath.toString(),
          )

          val startTime = System.currentTimeMillis()

          // check if danmu is initialized
          val isDanmuInitialized = if (downloadConfig.danmu) {
            try {
              withContext(Dispatchers.IO) {
                initDanmu(streamer, startTime, null, outputFolder)
              }
            } catch (e: Exception) {
              logger.error("(${streamer.name}) Danmu failed to initialize: $e")
              false
            }
          } else false

          val process = suspendCancellableCoroutine<StreamData?> {
            val builder = ProcessBuilder(*(cmds + arrayOf(outputPath.toString())))
            logger.info("(${streamer.name}) Starting parted download...")
            val process = builder
              .directory(File(app.ffmepgPath).parentFile)
              .redirectErrorStream(true)
              .start()

            it.invokeOnCancellation {
              process.destroy()
            }

            val danmakuProcess: Job? = if (isDanmuInitialized) launch {
              var danmuRetry = 0
              while (true) {
                logger.info("(${streamer.name}) Starting danmu download...")
                try {
                  withContext(Dispatchers.IO) {
                    danmu.fetchDanmu()
                  }
                } catch (e: CancellationException) {
                  // ignore cancellation exception because download process is finished
                  break
                } catch (e: IOException) {
                  logger.error("(${streamer.name}) Danmu download failed: $e")
                  logger.info("(${streamer.name}) Danmu download failed, $danmuRetry retry in 30 seconds...")
                  danmuRetry++
                  // delay 10 seconds before retrying
                  delay(10.toDuration(DurationUnit.SECONDS))
                }
              }
            } else null

            launch {
              while (process.isAlive) {
                process.inputStream.bufferedReader().readLine()?.let { line ->
                  if (!line.startsWith("size="))
                    logger.info("${streamer.name} - $line")
                }
              }
              logger.info("${streamer.name} - ffmpeg process is finished")
              // finish danmu download
              danmu.finish()
            }

            // show download progress info
            launch {
              while (process.isAlive) {
                delay(1.toDuration(DurationUnit.MINUTES))
                logger.info("(${streamer.name}) is downloading...")
              }
            }
            // wait for the download process to finish
            val exitCode = process.waitFor()
            val endTime = System.currentTimeMillis()
            danmakuProcess?.cancel("Danmu download process finished because the main download process finished")
            logger.debug("(${streamer.name}) download process finished, exit code: $exitCode")
            if (exitCode != 0) {
              logger.error("(${streamer.name}) download failed, exit code: $exitCode")
              it.resume(null)
            } else {
              streamData = streamData.copy(
                dateStart = startTime,
                dateEnd = endTime,
                outputFilePath = outputPath.toString().removeSuffix(".part"),
                danmuFilePath = danmu.danmuFile.absolutePath
              )
              it.resume(streamData)
            }
          }

          if (process == null) {
            logger.error("(${streamer.name}) could not download stream")
            // delete files if download failed
            outputPath.deleteFile()
            danmu.danmuFile.deleteFile()
            break
          } else {
            logger.debug("(${streamer.name}) downloaded: ${streamData.outputFilePath}")
            streamDataList.add(streamData)
            if (app.config.minPartSize > 0) {
              val fileSize = outputPath.toFile().length()
              if (fileSize < app.config.minPartSize) {
                logger.error("(${streamer.name}) file size too small: $fileSize")
                outputPath.deleteFile()
                break
              }
            }
            outputPath.rename(Path(outputPath.fileName.toString().removeSuffix(".part")))

            // on parted downloaded successfully
            downloadConfig.onPartedDownload(streamData)
          }
        }
        logger.debug("(${streamer.name}) finished download")
        streamDataList
      }
    }
  }

  private fun checkDiskSpace(path: Path, segmentPart: Long) {
    val fileStore = Files.getFileStore(path)
    val usableSpace = fileStore.usableSpace
    if (usableSpace < segmentPart) {
      logger.error("Not enough disk space: $usableSpace")
      throw IllegalStateException("Not enough disk space")
    }
  }

  private fun buildFFMpegRunningCmd(
    defaultFFmpegInputArgs: List<String>,
    defaultFFmpegOutputArgs: Array<String>,
    fileExtension: String,
  ) = arrayOf(
    app.ffmepgPath,
    "-y"
  ) + defaultFFmpegInputArgs + arrayOf(
    "-i",
    downloadUrl,
  ) + defaultFFmpegOutputArgs + arrayOf(
    "-c",
    "copy",
    "-f",
    fileExtension.removeSuffix(".part"),
  )

  private fun buildDefaultFFMpegInputArgs(cookies: String? = null) = mutableListOf<String>().apply {
    commonHeaders.forEach {
      val prefix = if (it.first == HttpHeaders.UserAgent) "-user_agent" else "-headers"
      add(prefix)
      add(it.second)
    }
    // ensure that the headers are properly separated
    add("-headers")
    add("\r\n")
    // add cookies if available
    cookies?.let {
      add("-cookies")
      add(it)
    }
    addAll(
      arrayOf(
        "-rw_timeout",
        "5000000",
      )
    )

    if (downloadUrl.contains(".m3u8"))
      addAll(
        arrayOf(
          "-max_reload",
          "1000",
        )
      )
  }

  protected suspend fun initDanmu(streamer: Streamer, startTime: Long, fileName: String? = null, folderPath: String? = null): Boolean =
    withContext(Dispatchers.IO) {
      danmu.init(streamer, startTime).run {
        if (this) {
          logger.info("(${streamer.name}) Danmu initialized")
          if (!folderPath.isNullOrEmpty()) {
            // check if the folder exists, if not create it
            val isFolderCreated = try {
              Files.exists(File(folderPath).toPath()) || File(folderPath).mkdirs()
            } catch (e: Exception) {
              logger.error("(${streamer.name}) Failed to create folder: $folderPath")
              false
            }
            if (!isFolderCreated) {
              logger.error("(${streamer.name}) Failed to create folder: $folderPath, falling back to default folder: danmu")
              danmu.fileFolder = "danmu"
            }
            danmu.fileFolder = folderPath
          }
          val formattedTime = toLocalDateTime(startTime, "yyyy-MM-dd HH-mm-ss")
          val finalName =
            if (fileName.isNullOrEmpty()) formatToFriendlyFileName("${streamer.name}-$downloadTitle-$formattedTime") + ".xml" else fileName
          danmu.fileName = formatToFriendlyFileName(finalName)
        } else {
          logger.error("${streamer.name}) Danmu failed to initialize")
        }
        this
      }
    }


  protected fun formatToFriendlyFileName(fileName: String): String {
    return fileName.replace(Regex("[/\n\r\t\u0000\u000c`?*\\\\<>|\":]"), "_").also {
      logger.debug("Formatted $fileName to $it")
    }
  }
}
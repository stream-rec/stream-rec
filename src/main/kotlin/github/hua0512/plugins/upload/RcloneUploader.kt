package github.hua0512.plugins.upload

import github.hua0512.UploadResult
import github.hua0512.app.App
import github.hua0512.data.upload.RcloneConfig
import github.hua0512.data.upload.UploadData
import github.hua0512.plugins.base.Upload
import kotlinx.coroutines.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.coroutines.resume

class RcloneUploader(app: App, override val uploadConfig: RcloneConfig) : Upload(app, uploadConfig) {

  companion object {
    @JvmStatic
    private val logger: Logger = LoggerFactory.getLogger(RcloneUploader::class.java)
  }

  override suspend fun uploadAction(uploadData: UploadData): Deferred<UploadResult?> = coroutineScope {
    val remotePath = uploadConfig.remotePath.also {
      if (it.isEmpty()) {
        logger.error("invalid remote path: $it, skipping...")
        return@coroutineScope async { null }
      }
    }
    // rclone copy <local file> <remote folder>
    val cmds = arrayOf(
      "rclone",
      "copy",
    )
    val extraCmds = uploadConfig.args.toTypedArray()

    val streamData = uploadData.streamData

    async(Dispatchers.IO) {
      // rclone copy <local file> <remote folder> --args

      // format dateStart to local time
      val startTimeString = LocalDateTime.ofInstant(
        Instant.ofEpochMilli(streamData.dateStart!!),
        ZoneId.systemDefault()
      )
      val streamer = streamData.streamer

      val toReplace = mapOf(
        "{streamer}" to streamer.name,
        "{title}" to streamData.title,
        "%yyyy" to startTimeString.format(DateTimeFormatter.ofPattern("yyyy")),
        "%MM" to startTimeString.format(DateTimeFormatter.ofPattern("MM")),
        "%dd" to startTimeString.format(DateTimeFormatter.ofPattern("dd")),
        "%HH" to startTimeString.format(DateTimeFormatter.ofPattern("HH")),
        "%mm" to startTimeString.format(DateTimeFormatter.ofPattern("mm")),
        "%ss" to startTimeString.format(DateTimeFormatter.ofPattern("ss")),
      )

      val replacedRemote = remotePath.run {
        var result = this
        toReplace.forEach { (k, v) ->
          result = result.replace(k, v)
        }
        result
      }

      val finalCmds = cmds + arrayOf(
        streamData.outputFilePath,
        replacedRemote
      ) + extraCmds
      val resultCode = suspendCancellableCoroutine<Int> {
        val builder = ProcessBuilder(*finalCmds)
          .redirectErrorStream(true)
          .start()

        it.invokeOnCancellation {
          builder.destroy()
        }
        launch {
          builder.inputStream.bufferedReader().readText().let { line ->
            logger.debug("rclone: $line")
          }
        }

        val code = builder.waitFor()
        it.resume(code)
      }
      val uploadResult: UploadResult
      if (resultCode != 0) {
        throw UploadFailedException(streamData.outputFilePath, "rclone failed with code: $resultCode")
      } else {
        logger.info("rclone: ${streamData.outputFilePath} finished")
        uploadResult = UploadResult(
          isSuccess = true,
          time = System.currentTimeMillis(),
          uploadData = listOf(
            uploadData.copy(
              isSuccess = true
            )
          ),
        )
      }
      uploadResult
    }
  }
}
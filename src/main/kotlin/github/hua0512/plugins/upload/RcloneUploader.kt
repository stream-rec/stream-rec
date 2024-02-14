package github.hua0512.plugins.upload

import github.hua0512.data.upload.UploadResult
import github.hua0512.app.App
import github.hua0512.data.upload.RcloneConfig
import github.hua0512.data.upload.UploadData
import github.hua0512.plugins.base.Upload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
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


  override suspend fun upload(uploadData: UploadData): UploadResult = withContext(Dispatchers.IO) {
    val remotePath = uploadConfig.remotePath.also {
      if (it.isEmpty()) {
        throw UploadInvalidArgumentsException("invalid remote path: $it")
      }
    }
    // rclone copy <local file> <remote folder>
    val cmds = arrayOf(
      "rclone",
      uploadConfig.rcloneOperation,
    )
    val extraCmds = uploadConfig.args.toTypedArray()

    // rclone "operation" <local file> <remote folder> --args
    // format dateStart to local time
    val startTimeString = LocalDateTime.ofInstant(
      Instant.ofEpochMilli(uploadData.streamStartTime),
      ZoneId.systemDefault()
    )
    val streamer = uploadData.streamer

    val toReplace = mapOf(
      "{streamer}" to streamer,
      "{title}" to uploadData.streamTitle,
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
      uploadData.filePath,
      replacedRemote
    ) + extraCmds

    val resultCode = suspendCancellableCoroutine {
      val builder = ProcessBuilder(*finalCmds)
        .redirectErrorStream(true)
        .start()

      it.invokeOnCancellation {
        builder.destroy()
      }
      launch {
        builder.inputStream.bufferedReader().readText().let { line ->
          logger.debug(line)
        }
      }

      val code = builder.waitFor()
      it.resume(code)
    }

    if (resultCode != 0) {
      throw UploadFailedException("rclone failed with exit code: $resultCode", uploadData.filePath)
    } else {
      logger.info("rclone: ${uploadData.filePath} finished")
      UploadResult(System.currentTimeMillis(), true, "", uploadData.filePath)
    }
  }
}
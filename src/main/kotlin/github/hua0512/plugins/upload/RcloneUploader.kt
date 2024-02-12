package github.hua0512.plugins.upload

import github.hua0512.UploadResult
import github.hua0512.app.App
import github.hua0512.data.upload.RcloneConfig
import github.hua0512.data.upload.UploadData
import github.hua0512.plugins.base.Upload
import kotlinx.coroutines.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
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
      val finalCmds = cmds + arrayOf(
        streamData.outputFilePath,
        remotePath
      ) + extraCmds
      val status = suspendCancellableCoroutine<Boolean> {
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
        if (code != 0) {
          logger.error("rclone: failed, exit code: $code")
          it.resume(false)
        } else {
          it.resume(true)
        }
      }
      val uploadResult: UploadResult
      if (!status) {
        logger.error("rclone: ${streamData.outputFilePath} failed")
        uploadResult = UploadResult(
          isSuccess = false,
          time = System.currentTimeMillis(),
          uploadData = listOf(uploadData),
        )
      } else {
        logger.info("rclone: ${streamData.outputFilePath} finished")
        uploadResult = UploadResult(
          isSuccess = true,
          time = System.currentTimeMillis(),
          uploadData = listOf(uploadData),
        )
      }
      uploadResult
    }
  }
}
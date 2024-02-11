package github.hua0512.plugins.upload

import github.hua0512.UploadResult
import github.hua0512.app.App
import github.hua0512.data.UploadData
import github.hua0512.plugins.base.Upload
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.withPermit
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class RcloneUploader(app: App) : Upload(app) {

  companion object {
    @JvmStatic
    private val logger: Logger = LoggerFactory.getLogger(RcloneUploader::class.java)
  }

  private val uploadJobs = mutableListOf<Deferred<UploadResult>>()

  override suspend fun upload(data: List<UploadData>): List<UploadResult> = coroutineScope {
    app.uploadSemaphore.withPermit {
      println("Uploading data: $data")

      if (data.isEmpty()) return@coroutineScope emptyList()

      // rclone copy <local file> <remote folder>
      val cmds = arrayOf(
        "rclone",
        "copy",
      )


      emptyList()
      /**
      val streamingStartDate = data.first().dateStart?.run {
      // convert timestamp and format to yyyy-MM-dd
      LocalDateTime.ofInstant(Instant.ofEpochMilli(this), TimeZone.getDefault().toZoneId()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
      } ?: throw Exception("No start date found")

      val remoteFolder = "onedrive:records/${streamer.name}/$streamingStartDate"

      data.forEach { streamData ->

      if (streamData.outputFilePath.isEmpty()) {
      logger.error("invalid file: ${streamData.outputFilePath}")
      return@forEach
      }
      File(streamData.outputFilePath).run {
      if (length() == 0L || !exists()) {
      logger.error("invalid file: ${streamData.outputFilePath}")
      return@forEach
      }
      this
      }

      val job = async(Dispatchers.IO) {
      // rclone copy <local file> <remote folder> --onedrive-chunk-size 100M -P
      val finalCmds = cmds + arrayOf(
      streamData.outputFilePath,
      remoteFolder,
      "--onedrive-chunk-size",
      "100M",
      "-P",
      )

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

      val uploadData: UploadData
      if (!status) {
      logger.error("rclone: ${streamData.outputFilePath} failed")
      uploadData = UploadResult(
      streamerId = streamer.id,
      streamDataId = streamData.id?.toLong() ?: 0,
      isSuccess = false,
      )
      } else {
      logger.info("rclone: ${streamData.outputFilePath} finished")
      uploadData = UploadData(
      streamerId = streamer.id,
      streamDataId = streamData.id?.toLong() ?: 0,
      isSuccess = true,
      )
      }
      uploadData
      }
      uploadJobs.add(job)
      }

      return@coroutineScope withContext(Dispatchers.IO) {
      uploadJobs.run {
      awaitAll().also {
      clear()
      }
      }
      }
       **/
    }
  }
}
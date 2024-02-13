package github.hua0512.plugins.base

import github.hua0512.UploadResult
import github.hua0512.app.App
import github.hua0512.data.upload.UploadConfig
import github.hua0512.data.upload.UploadData
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path

abstract class Upload(protected val app: App, open val uploadConfig: UploadConfig?) {

  companion object {
    @JvmStatic
    private val logger = LoggerFactory.getLogger(Upload::class.java)

  }

  open suspend fun upload(uploadDataList: List<UploadData>): List<UploadResult> {
    logger.info("Uploading data: $uploadDataList")

    if (uploadDataList.isEmpty()) return emptyList()

    val uploadResults = mutableListOf<Deferred<UploadResult?>>()

    uploadDataList.forEach { uploadData ->
      val streamData = uploadData.streamData.also {
        if (it.outputFilePath.isEmpty() || it.outputFilePath.isBlank()) {
          logger.error("${it.outputFilePath} invalid local file path, skipping...")
          return@forEach
        }
        if (!Files.exists(Path.of(it.outputFilePath))) {
          logger.error("${it.outputFilePath} invalid local file, file do not exists, skipping...")
          return@forEach
        }
        if (Files.size(Path.of(it.outputFilePath)) == 0L) {
          logger.error("${it.outputFilePath} invalid local file, zero length, skipping...")
          return@forEach
        }
      }
      app.uploadSemaphore.withPermit {
        withContext(Dispatchers.IO) {
          uploadAction(uploadData)
        }
      }.also {
        uploadResults.add(it)
      }
    }
    val results = uploadResults.awaitAll()
    logger.info("Upload results: $results")
    if (results.any { it == null } || results.any { !it!!.isSuccess }) {
      logger.error("Some uploads failed, check logs for details")
    }
    return results.filterNotNull()
  }

  abstract suspend fun uploadAction(uploadData: UploadData): Deferred<UploadResult?>
}
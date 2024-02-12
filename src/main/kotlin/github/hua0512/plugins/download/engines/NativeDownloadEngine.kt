package github.hua0512.plugins.download.engines

import github.hua0512.app.App
import github.hua0512.data.StreamData
import github.hua0512.logger
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * @author hua0512
 * @date : 2024/2/12 18:45
 */
class NativeDownloadEngine(override val app: App) : BaseDownloadEngine(app) {

  override suspend fun startDownload(): StreamData? {
    val outputFile = Path.of(downloadFilePath).toFile()

    return try {
      withContext(Dispatchers.IO) {
        Files.createFile(outputFile.toPath())
      }

      withContext(Dispatchers.IO) {
        app.client.prepareGet(downloadUrl!!) {
          headers {
            this@NativeDownloadEngine.headers.forEach { (t, u) ->
              append(t, u)
            }
          }
        }.execute { response ->
          val channel: ByteReadChannel = response.bodyAsChannel()
          while (!channel.isClosedForRead) {
            val packet = channel.readRemaining(DEFAULT_BUFFER_SIZE.toLong())
            while (!packet.isEmpty) {
              val bytes = packet.readBytes()
              if (outputFile.length() + bytes.size > fileLimitSize) {
                logger.error("File size limit reached")
                return@execute
              }
              Files.write(outputFile.toPath(), bytes, StandardOpenOption.APPEND)
            }
          }
        }
      }
      streamData?.copy(
        dateStart = startTime,
        dateEnd = System.currentTimeMillis(),
        outputFilePath = downloadFilePath.removeSuffix(".part"),
      )
    } catch (e: Exception) {
      logger.error("Error downloading stream", e)
      return null
    }
  }
}
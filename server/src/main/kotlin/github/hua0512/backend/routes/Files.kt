import github.hua0512.backend.logger
import github.hua0512.data.StreamDataId
import github.hua0512.repo.stream.StreamDataRepo
import github.hua0512.utils.md5
import io.ktor.http.ContentDisposition
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.http.content.LocalPathContent
import io.ktor.server.plugins.partialcontent.PartialContent
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.options
import io.ktor.server.routing.route
import io.ktor.utils.io.CancellationException
import kotlinx.serialization.Serializable
import java.io.File
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.text.Charsets

@Serializable
data class StreamDataFiles(
  val id: Long,
  val files: List<FileInfo>
)

@Serializable
data class FileInfo(
  val name: String,
  val size: Long,
  val contentType: String,
  val exists: Boolean,
  val hash: String,
  val type: String
)

fun Route.filesRoute(streamDataRepo: StreamDataRepo) {
  route("/files") {
    install(PartialContent)

    // Get file information endpoint
    get("{id}") {
      val id = call.parameters["id"]?.toLongOrNull()
      if (id == null) {
        call.respond(HttpStatusCode.BadRequest, "Invalid stream data id")
        return@get
      }

      try {
        val streamData = streamDataRepo.getStreamDataById(StreamDataId(id))
        if (streamData == null) {
          call.respond(HttpStatusCode.NotFound, "Stream data not found")
          return@get
        }

        val files = mutableListOf<FileInfo>()

        // Add video file info if exists
        streamData.outputFilePath?.let { path ->
          val file = File(path)
          val fileName = Path.of(path).fileName.toString()
          val contentType = when {
            fileName.endsWith(".mp4") -> "video/mp4"
            fileName.endsWith(".flv") -> "video/x-flv"
            else -> "application/octet-stream"
          }
          val hash = generateFileHash(id, path, "video")
          files.add(
            FileInfo(
              name = fileName,
              size = if (file.exists()) file.length() else 0,
              contentType = contentType,
              exists = file.exists(),
              hash = hash,
              type = "video"
            )
          )
        }

        // Add danmu file info if exists
        streamData.danmuFilePath?.let { path ->
          val file = File(path)
          val fileName = Path.of(path).fileName.toString()
          val hash = generateFileHash(id, path, "danmu")
          files.add(
            FileInfo(
              name = fileName,
              size = if (file.exists()) file.length() else 0,
              contentType = "application/xml",
              exists = file.exists(),
              hash = hash,
              type = "danmu"
            )
          )
        }

        call.respond(StreamDataFiles(id = id, files = files))

      } catch (e: Exception) {
        logger.error("Error getting file information", e)
        call.respond(HttpStatusCode.InternalServerError, "Error getting file information: ${e.message}")
      }
    }

    // Common function to validate and get file path
    suspend fun validateAndGetFilePath(id: Long, hashWithExt: String): Pair<File?, String?> {
      val streamData = streamDataRepo.getStreamDataById(StreamDataId(id))
        ?: return null to "Stream data not found"

      // Extract hash and extension
      val lastDotIndex = hashWithExt.lastIndexOf('.')
      if (lastDotIndex == -1) {
        return null to "Invalid file format"
      }

      val hash = hashWithExt.substring(0, lastDotIndex)
      val extension = hashWithExt.substring(lastDotIndex)

      // Check video file
      val videoHash = streamData.outputFilePath?.let { path ->
        val ext = Path.of(path).fileName.toString().substringAfterLast('.')
        "${generateFileHash(id, path, "video")}.$ext"
      }
      val danmuHash = streamData.danmuFilePath?.let { path ->
        val ext = Path.of(path).fileName.toString().substringAfterLast('.')
        "${generateFileHash(id, path, "danmu")}.$ext"
      }

      val filePath = when (hashWithExt) {
        videoHash -> streamData.outputFilePath
        danmuHash -> streamData.danmuFilePath
        else -> null
      }

      if (filePath.isNullOrBlank()) {
        return null to "File not found"
      }

      val file = File(filePath)
      if (!file.exists()) {
        return null to "File not found"
      }

      return file to null
    }

    // Add file existence check endpoint
    get("{id}/{hash}/exists") {
      val id = call.parameters["id"]?.toLongOrNull()
      val hashWithExt = call.parameters["hash"]

      if (id == null || hashWithExt.isNullOrBlank()) {
        call.respond(HttpStatusCode.BadRequest, "Invalid parameters")
        return@get
      }

      try {
        val (file, error) = validateAndGetFilePath(id, hashWithExt)
        when {
          error != null -> call.respond(HttpStatusCode.NotFound, error)
          file != null -> {
            val isVideo = file.extension != "xml"

            // return file info
            call.respond(
              HttpStatusCode.OK, FileInfo(
                name = file.name,
                size = file.length(),
                contentType = "application/octet-stream",
                exists = true,
                hash = hashWithExt,
                type = if (isVideo) "video" else "danmu"
              )
            )
          }

          else -> call.respond(HttpStatusCode.NotFound)
        }
      } catch (e: Exception) {
        logger.error("Error checking file existence", e)
        call.respond(HttpStatusCode.InternalServerError, "Error checking file: ${e.message}")
      }
    }

    // File serving endpoint
    get("{id}/{hash}") {
      val id = call.parameters["id"]?.toLongOrNull()
      val hashWithExt = call.parameters["hash"]

      if (id == null || hashWithExt.isNullOrBlank()) {
        call.respond(HttpStatusCode.BadRequest, "Invalid parameters")
        return@get
      }

      try {
        val (file, error) = validateAndGetFilePath(id, hashWithExt)
        if (error != null || file == null) {
          call.respond(HttpStatusCode.NotFound, error ?: "File not found")
          return@get
        }

        logger.debug("Serving file: {}", file)

//        call.request.headers.forEach { key, value ->
//          logger.debug("request Header: $key: $value")
//        }

        val fileName = file.name
        call.response.header(
          HttpHeaders.ContentDisposition,
          ContentDisposition.Attachment.withParameter(
            ContentDisposition.Parameters.FileName,
            fileName
          ).toString()
        )

        call.respondFile(file)

      } catch (e: Exception) {
        // if connection is already terminated, ignore the exception
        if (call.response.status() == null) {
          return@get
        }
        // if connection is reset by peer, ignore the exception
        if (e.message?.contains("Cannot write to a channel") == true) {
          return@get
        }
        if (e is CancellationException)
          return@get // ignore CancellationException

        logger.error("Error serving file", e)
        call.respond(HttpStatusCode.InternalServerError, "Error serving file: ${e.message}")
      }
    }
  }
}

private fun generateFileHash(id: Long, path: String, type: String): String {
  return path.md5()
}
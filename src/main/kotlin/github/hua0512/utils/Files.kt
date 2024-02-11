package github.hua0512.utils

import github.hua0512.logger
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * @author hua0512
 * @date : 2024/2/11 21:57
 */

fun Path.deleteFile(): Boolean = try {
  Files.deleteIfExists(this)
} catch (e: Exception) {
  logger.error("Could not delete file: $this")
  false
}

fun File.deleteFile(): Boolean = toPath().deleteFile()

fun Path.rename(newPath: Path): Boolean = try {
  Files.move(this, newPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.COPY_ATTRIBUTES)
  newPath.toFile().exists()
} catch (e: Exception) {
  logger.error("Could not rename file: $this to $newPath")
  false
}

fun File.rename(newFile: File): Boolean = toPath().rename(newFile.toPath())
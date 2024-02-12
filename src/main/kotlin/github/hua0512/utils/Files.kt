package github.hua0512.utils

import github.hua0512.logger
import java.io.File
import java.nio.file.CopyOption
import java.nio.file.Files
import java.nio.file.Path

/**
 * @author hua0512
 * @date : 2024/2/11 21:57
 */

fun Path.deleteFile(): Boolean = try {
  Files.deleteIfExists(this).also {
    if (it) logger.debug("File deleted: {}", this)
    else logger.debug("File not found: {}", this)
  }
} catch (e: Exception) {
  logger.error("Could not delete file: $this")
  false
}

fun File.deleteFile(): Boolean = toPath().deleteFile()

fun Path.rename(newPath: Path, vararg options: CopyOption): Boolean = try {
  Files.move(this, newPath, *options)
  newPath.toFile().exists().also {
    if (it) logger.debug("File renamed: {} to {}", this, newPath)
    else logger.debug("File not renamed: {} to {}", this, newPath)
  }
} catch (e: Exception) {
  logger.error("Could not rename file: $this to $newPath")
  false
}

fun File.rename(newFile: File): Boolean = toPath().rename(newFile.toPath())
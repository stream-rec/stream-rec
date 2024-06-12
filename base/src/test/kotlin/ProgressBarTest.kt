import github.hua0512.plugins.download.ConsoleMultiBarConsumer
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import me.tongfei.progressbar.ProgressBarBuilder
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertNotNull


class ProgressBarTest {


  @Test
  fun testMultipleBars() = runTest {
    val test = ProgressBarBuilder()
      .setTaskName("#01")
      .setInitialMax(100)
      .setConsumer(ConsoleMultiBarConsumer(PrintStream(FileOutputStream(FileDescriptor.out)), 1))
      .hideEta()
      .setUpdateIntervalMillis(100)
      .build()

    val test2 = ProgressBarBuilder()
      .setTaskName("#02")
      .setInitialMax(100)
      .setConsumer(ConsoleMultiBarConsumer(PrintStream(FileOutputStream(FileDescriptor.out)), 2))
      .hideEta()
      .setUpdateIntervalMillis(100)
      .build()

    val test3 = ProgressBarBuilder()
      .setTaskName("#03")
      .setInitialMax(100)
      .setConsumer(ConsoleMultiBarConsumer(PrintStream(FileOutputStream(FileDescriptor.out)), 3))
      .hideEta()
      .setUpdateIntervalMillis(100)
      .build()
    assertNotNull(test)
  }

}
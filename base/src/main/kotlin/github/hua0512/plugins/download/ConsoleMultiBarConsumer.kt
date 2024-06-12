package github.hua0512.plugins.download

import me.tongfei.progressbar.ConsoleProgressBarConsumer
import java.io.PrintStream

class ConsoleMultiBarConsumer(val out: PrintStream, val index: Int) : ConsoleProgressBarConsumer(out) {


  override fun accept(str: String?) {
    out.print("\r\n")
    out.print(str)
    //super.accept(str);
    out.print("\u001B[F")
  }
}
package github.hua0512.plugins.download

import github.hua0512.logger
import me.tongfei.progressbar.DelegatingProgressBarConsumer
import me.tongfei.progressbar.ProgressBar
import me.tongfei.progressbar.ProgressBarBuilder
import me.tongfei.progressbar.ProgressBarStyle
import kotlin.time.DurationUnit
import kotlin.time.toDuration

object ProgressBarManager {

  private val bars = mutableMapOf<String, ProgressBar>()

  fun addProgressBar(url: String, taskName: String, max: Long = 0): ProgressBar {

    if (bars.containsKey(url)) {
      return bars[url]!!
    }

    val progressBar = ProgressBarBuilder()
      .setTaskName(taskName)
      .setConsumer(DelegatingProgressBarConsumer(logger::info))
      .setInitialMax(max)
      .setUpdateIntervalMillis(2.toDuration(DurationUnit.MINUTES).inWholeMilliseconds.toInt())
      .continuousUpdate()
      .hideEta()
      .setStyle(ProgressBarStyle.COLORFUL_UNICODE_BAR)
      .build()
    bars[url] = progressBar
    return progressBar
  }

  fun getProgressBar(url: String) = bars[url]


  fun deleteProgressBar(url: String) {
    getProgressBar(url)?.let {
      it.close()
      bars.remove(url)
    }
  }

}
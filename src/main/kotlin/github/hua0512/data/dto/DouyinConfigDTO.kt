package github.hua0512.data.dto

import github.hua0512.data.platform.DouyinQuality

/**
 * @author hua0512
 * @date : 2024/2/11 20:00
 */
interface DouyinConfigDTO : DownloadConfigDTO {

  val quality: DouyinQuality?
}
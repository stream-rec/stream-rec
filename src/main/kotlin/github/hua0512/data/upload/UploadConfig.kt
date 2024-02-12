package github.hua0512.data.upload

import kotlinx.serialization.Serializable

@Serializable
sealed class UploadConfig(val platform: UploadPlatform) {

}

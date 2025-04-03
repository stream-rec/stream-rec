package github.hua0512.data.dto

interface IOutputFile {
  var path: String
  var size: Long
  var streamerName: String?
  var streamerPlatform: String?
  var streamTitle: String?
  var streamDate: Long?
  var streamDataId: Long
}
rootProject.name = "stream-rec"
include("stream-rec")

dependencyResolutionManagement {
  repositories {
    mavenCentral()
    mavenLocal()
    maven(url = "https://jitpack.io")
  }
}
include("base")
include("stream-rec-backend")
include("platforms:huya")
findProject(":platforms:huya")?.name = "huya"
include("platforms:douyin")
findProject(":platforms:douyin")?.name = "douyin"

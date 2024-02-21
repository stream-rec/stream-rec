rootProject.name = "stream-rec"
include("stream-rec")

dependencyResolutionManagement {
  repositories {
    mavenCentral()
    mavenLocal()
    maven(url = "https://jitpack.io")
  }
}
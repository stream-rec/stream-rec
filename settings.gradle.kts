rootProject.name = "stream-rec"

//enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
pluginManagement {
  repositories {
    google()
    gradlePluginPortal()
    mavenCentral()
  }
}

dependencyResolutionManagement {
  repositories {
    mavenCentral()
    mavenLocal()
    google()
    maven(url = "https://jitpack.io")
  }
}
include("base")
include("platforms")
include("stream-rec")
include("stream-rec-backend")
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
  @Suppress("UnstableApiUsage")
  repositories {
    mavenCentral()
    mavenLocal()
    google()
    maven(url = "https://jitpack.io")
  }
}

arrayOf("base", "common", "platforms", "stream-rec", "server", "flv-processing", "hls-processing").forEach {
  include(it)
}
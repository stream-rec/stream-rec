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

arrayOf("base", "platforms", "stream-rec", "stream-rec-backend", "flv-processing").forEach {
  include(it)
}

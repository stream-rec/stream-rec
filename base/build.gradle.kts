plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.app.cash.sqldelight)
}

group = "github.hua0512.streamrec"
version = "0.5.0"

dependencies {
  implementation(libs.ch.qos.logback.classic)
  implementation(libs.org.jetbrains.kotlinx.serialization.json)
  api(libs.app.cash.sqldelight.sqlite.driver)
  api(libs.app.cash.sqldelight.coroutines.extensions)
  api(libs.app.cash.sqldelight.primitive.adapters)
  implementation(libs.org.jetbrains.kotlinx.datetime)
  implementation(libs.me.tongfei.progressbar)
  api(libs.io.ktor.client.core)
  api(libs.io.ktor.client.cio)
  api(libs.io.ktor.serialization.kotlinx.json)
  api(libs.io.ktor.client.logging)
}

kotlin {
  jvmToolchain(17)
}

sqldelight {
  databases {
    create("StreamRecDatabase") {
      packageName.set("github.hua0512")
    }
  }
}
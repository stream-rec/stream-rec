plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.app.cash.sqldelight)
}

// read the version from the gradle.properties file
val versionName: String by project
val groupName: String by project
group = groupName
version = versionName

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

  testImplementation(libs.bundles.test.jvm)
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
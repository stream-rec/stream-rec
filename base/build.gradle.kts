plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.app.cash.sqldelight)
  alias(libs.plugins.ksp)
  alias(libs.plugins.room)
}

// read the version from the gradle.properties file
val versionName: String by project
val groupName: String by project
group = groupName
version = versionName

dependencies {
  implementation(libs.ch.qos.logback.classic)
  implementation(libs.org.jetbrains.kotlinx.serialization.json)
  implementation(libs.org.jetbrains.kotlinx.coroutines.core)
  implementation(libs.androidx.room.runtime)
  ksp(libs.androidx.room.compiler)
  api(libs.app.cash.sqldelight.sqlite.driver)
  api(libs.app.cash.sqldelight.coroutines.extensions)
  api(libs.app.cash.sqldelight.primitive.adapters)
  implementation(libs.org.jetbrains.kotlinx.datetime)
  implementation(libs.me.tongfei.progressbar)
  implementation(libs.io.ktor.client.core)
  implementation(libs.io.ktor.client.cio)
  implementation(libs.io.ktor.client.encoding)
  implementation(libs.io.ktor.client.content.negotiation)
  implementation(libs.io.ktor.serialization.kotlinx.json)
  implementation(libs.io.ktor.client.logging)
  testImplementation(libs.bundles.test.jvm)
}

sqldelight {
  databases {
    create("StreamRecDatabase") {
      packageName.set("github.hua0512")
    }
  }
}

room {
  schemaDirectory("$projectDir/schemas")
}
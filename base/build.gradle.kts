plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.serialization)
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
  implementation(libs.org.jetbrains.kotlinx.serialization.core)
  implementation(libs.org.jetbrains.kotlinx.serialization.json)
  implementation(libs.org.jetbrains.kotlinx.coroutines.core)
  implementation(libs.androidx.room.runtime)
  ksp(libs.androidx.room.compiler)
  implementation(libs.com.michael.bull.kotlin.result)
  implementation(libs.org.jetbrains.kotlinx.datetime)
  implementation(libs.me.tongfei.progressbar)
  implementation(libs.io.ktor.client.core)
  implementation(libs.io.ktor.client.okhttp)
  implementation(libs.io.ktor.client.encoding)
  implementation(libs.io.ktor.client.content.negotiation)
  implementation(libs.io.ktor.serialization.kotlinx.json)
  implementation(libs.io.ktor.client.logging)
  implementation(libs.io.exoquery.pprint)
  implementation(libs.io.lindstrom.m3u8.parser)
  implementation(project(":flv-processing"))
  implementation(project(":hls-processing"))
  implementation(project(":common"))
  testImplementation(libs.bundles.test.jvm)
}

tasks.test {
  useJUnitPlatform()
}


room {
  schemaDirectory("$projectDir/schemas")
}
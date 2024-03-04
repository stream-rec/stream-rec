plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.serialization)
}

group = "github.hua0512.streamrec"
version = "0.5.0"

dependencies {
  implementation(libs.io.ktor.server.core.jvm)
  implementation(libs.io.ktor.server.host.common.jvm)
  api(libs.io.ktor.server.netty.jvm)
  implementation(libs.io.ktor.server.auth.jvm)
  implementation(libs.io.ktor.server.auth.jwt.jvm)
  implementation(libs.io.ktor.server.status.pages.jvm)
  implementation(libs.io.ktor.server.cors.jvm)
  implementation(libs.io.ktor.server.call.logging.jvm)
  implementation(libs.io.ktor.server.call.id.jvm)
  implementation(libs.io.ktor.server.content.negotiation.jvm)
  implementation(libs.io.ktor.serialization.kotlinx.json.jvm)
  implementation(libs.io.ktor.server.websockets.jvm)
  implementation(libs.ch.qos.logback.classic)
  testImplementation(libs.bundles.test.jvm)
  testImplementation(libs.io.ktor.server.tests.jvm)
//  testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")
}

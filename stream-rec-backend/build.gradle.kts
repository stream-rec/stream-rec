plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.serialization)
}

// read the version from the gradle.properties file
val versionName: String by project
val groupName: String by project
group = groupName
version = versionName

dependencies {
  implementation(project(":base"))
  implementation(project(":common"))
  implementation(libs.at.favre.lib.bcrypt)
  implementation(libs.org.jetbrains.kotlinx.datetime)
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

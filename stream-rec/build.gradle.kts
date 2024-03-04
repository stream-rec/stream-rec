plugins {
  alias(libs.plugins.ktor)
  alias(libs.plugins.kotlin.jvm)
  id("com.google.devtools.ksp") version "1.9.22-1.0.17"
}

project.ext.set("development", true)

group = "github.hua0512.streamrec"
version = "0.5.0"

application {
  mainClass.set("github.hua0512.Application")

  val isDevelopment: Boolean = project.ext.has("development")
  applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}


ktor {
  fatJar {
    archiveFileName.set("stream-rec.jar")
  }
}

dependencies {
  implementation(libs.ch.qos.logback.classic)
  implementation(libs.org.jetbrains.kotlinx.coroutines.core)
  implementation(libs.org.jetbrains.kotlinx.serialization.json)
  implementation(libs.com.google.dagger.dagger)
  implementation("net.peanuuutz.tomlkt:tomlkt:0.3.7")
  implementation(libs.org.jetbrains.kotlinx.datetime)
  implementation(project(":base"))
  implementation(project(":platforms:douyin"))
  implementation(project(":platforms:huya"))
  implementation(project(":stream-rec-backend"))

  ksp(libs.com.google.dagger.dagger.compiler)
  testImplementation(libs.bundles.test.jvm)
}

tasks.test {
  useJUnitPlatform()
}

kotlin {
  jvmToolchain(17)
}
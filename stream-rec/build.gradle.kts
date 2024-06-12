plugins {
  alias(libs.plugins.ktor)
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.ksp)
}

project.ext.set("development", false)

// read the version from the gradle.properties file
val versionName: String by project
val groupName: String by project
group = groupName
version = versionName

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
  implementation(libs.io.ktor.client.core)
  implementation(libs.org.jetbrains.kotlinx.coroutines.core)
  implementation(libs.org.jetbrains.kotlinx.serialization.json)
  implementation(libs.com.google.dagger.dagger)
  implementation(libs.org.jetbrains.kotlinx.datetime)
  implementation(project(":base"))
  implementation(project(":platforms"))
  implementation(project(":stream-rec-backend"))
  implementation(libs.androidx.sqlite.bundled)
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.compiler)
  implementation(libs.me.tongfei.progressbar)
  ksp(libs.com.google.dagger.dagger.compiler)
  testImplementation(libs.bundles.test.jvm)
}

tasks.test {
  useJUnitPlatform()
}
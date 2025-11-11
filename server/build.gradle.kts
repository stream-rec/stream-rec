import java.io.ByteArrayOutputStream

plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.serialization)
}

// Read the version from the gradle.properties file
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
  implementation(libs.io.ktor.server.auto.head.response)
  implementation(libs.io.ktor.server.content.negotiation.jvm)
  implementation(libs.io.ktor.server.partial.content)
  implementation(libs.io.ktor.serialization.kotlinx.json.jvm)
  implementation(libs.com.michael.bull.kotlin.result)
  implementation(libs.io.ktor.server.websockets.jvm)
  implementation(libs.ch.qos.logback.classic)
  testImplementation(libs.bundles.test.jvm)
  testImplementation(libs.io.ktor.server.tests.jvm)
}

private fun execAndCapture(vararg command: String): String {
  return providers.exec {
    commandLine(command.toList())
  }.standardOutput.asText.get().trim()
}

tasks.register("getGitVersion") {
  doLast {
    try {
      val gitVersion = execAndCapture("git", "describe", "--tags", "--always", "--first-parent")
      val commitHash = execAndCapture("git", "rev-parse", "HEAD")
      val gitCommitCount = execAndCapture("git", "rev-list", "--count", "HEAD")
      val finalCommitCount = Integer.parseInt(gitCommitCount) + 10000
      println("Git version: $gitVersion")
      println("Commit hash: $commitHash")
      println("Commit count: $finalCommitCount")

      project.extra["gitVersion"] = gitVersion
      project.extra["gitCommitHash"] = commitHash
      project.extra["gitCommitCount"] = finalCommitCount
    } catch (e: Exception) {
      println("Error executing git commands: ${e.message}")
      project.extra["gitVersion"] = "N/A"
      project.extra["gitCommitHash"] = "N/A"
      project.extra["gitCommitCount"] = "N/A"
    }
  }
}

tasks.processResources {
  dependsOn("getGitVersion")
  filesMatching("server.properties") {
    println("Replacing placeholders in server.properties")
    val props = mapOf(
      "gitVersion" to (project.extra["gitVersion"] as String),
      "gitCommitHash" to (project.extra["gitCommitHash"] as String),
      "gitCommitCount" to (project.extra["gitCommitCount"] as Any),
    )
    expand(props)
  }
}

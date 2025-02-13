/*
 * MIT License
 *
 * Stream-rec  https://github.com/hua0512/stream-rec
 *
 * Copyright (c) 2025 hua0512 (https://github.com/hua0512)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

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
  implementation(libs.at.favre.lib.bcrypt)
  implementation(libs.ch.qos.logback.classic)
  implementation(libs.io.ktor.client.core)
  implementation(libs.org.jetbrains.kotlinx.atomicfu)
  implementation(libs.org.jetbrains.kotlinx.coroutines.core)
  implementation(libs.org.jetbrains.kotlinx.serialization.json)
  implementation(libs.com.google.dagger.dagger)
  implementation(libs.org.jetbrains.kotlinx.datetime)
  implementation(libs.com.michael.bull.kotlin.result)
  implementation(project(":base"))
  implementation(project(":common"))
  implementation(project(":platforms"))
  implementation(project(":server"))
  implementation(project(":flv-processing"))
  implementation(libs.androidx.sqlite)
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
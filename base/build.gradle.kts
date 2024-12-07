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
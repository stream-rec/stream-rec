plugins {
  kotlin("jvm") version "1.9.22"
  kotlin("plugin.serialization") version "1.9.22"
  id("com.google.devtools.ksp") version "1.9.22-1.0.17"
}

group = "hua0512.me"
version = "0.0.1"

repositories {
  mavenCentral()
}

dependencies {
  implementation(libs.ch.qos.logback.classic)
  implementation(libs.io.ktor.client.core)
  implementation(libs.io.ktor.client.cio)
  implementation(libs.io.ktor.client.java)
  implementation(libs.io.ktor.client.content.negotiation)
  implementation(libs.io.ktor.serialization.kotlinx.json)
  implementation(libs.io.ktor.client.logging)
  implementation(libs.io.ktor.client.okhttp)
  implementation(libs.org.jetbrains.kotlinx.serialization.json)
  implementation(libs.com.google.dagger.dagger)
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-debug:1.4.0")
  implementation("org.jetbrains.exposed:exposed-core:0.47.0")
  implementation("org.jetbrains.exposed:exposed-crypt:0.47.0")
  implementation("org.jetbrains.exposed:exposed-dao:0.47.0")
  implementation("org.jetbrains.exposed:exposed-json:0.47.0")
  implementation("org.jetbrains.exposed:exposed-kotlin-datetime:0.47.0")
  implementation("org.redundent:kotlin-xml-builder:1.9.1")
  implementation("com.tencent.tars:tars-core:1.7.3")
  implementation("net.peanuuutz.tomlkt:tomlkt:0.3.7")

  ksp(libs.com.google.dagger.dagger.compiler)

  testImplementation(libs.org.jetbrains.kotlin.test.junit)
  testImplementation("org.mockito:mockito-core:3.12.4")
  testImplementation("org.junit.jupiter:junit-jupiter:5.8.2")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.6.0")

}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
  kotlinOptions {
    jvmTarget = JavaVersion.VERSION_11.toString()
    freeCompilerArgs = listOf("-Xopt-in=kotlin.RequiresOptIn")
  }
}

plugins {
  alias(libs.plugins.kotlin.jvm)
  kotlin("plugin.serialization") version "1.9.22"
  id("com.google.devtools.ksp") version "1.9.22-1.0.17"
  alias(libs.plugins.ktor)
//  id("com.google.protobuf") version "0.9.4"
}

group = "github.hua0512"
version = "0.0.1"

application.mainClass.set("github.hua0512.Application")

ktor {
  fatJar {
    archiveFileName.set("stream-rec.jar")
  }
}
// disable protoc plugin because we have the generated files
//protobuf {
//  // Configure the protoc executable
//  protoc {
//    // Download from repositories
//    artifact = "com.google.protobuf:protoc:3.25.2"
//  }
//  generateProtoTasks {
//    ofSourceSet("main").forEach { task ->
//      task.builtins {
//        getByName("java") {
//          option("lite")
//        }
//      }
//    }
//  }
//}

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
  implementation("org.redundent:kotlin-xml-builder:1.9.1")
  implementation("com.tencent.tars:tars-core:1.7.3")
  implementation("net.peanuuutz.tomlkt:tomlkt:0.3.7")
  implementation("me.tongfei:progressbar:0.10.0")
  implementation("com.google.protobuf:protobuf-java-util:3.25.2")
  implementation("com.google.protobuf:protobuf-javalite:3.25.2")
  implementation("com.google.protobuf:protobuf-kotlin-lite:3.25.2")
  ksp(libs.com.google.dagger.dagger.compiler)

  testImplementation(libs.org.jetbrains.kotlin.test.junit)
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-debug:1.4.0")
  testImplementation("org.mockito:mockito-core:3.12.4")
  testImplementation("org.junit.jupiter:junit-jupiter:5.8.2")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.6.0")

}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
  kotlinOptions {
    jvmTarget = JavaVersion.VERSION_11.toString()
    freeCompilerArgs = listOf("-opt-in=kotlin.RequiresOptIn")
  }
}

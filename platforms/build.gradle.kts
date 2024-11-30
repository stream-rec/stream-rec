plugins {
  alias(libs.plugins.kotlin.jvm)
  //  id("com.google.protobuf") version "0.9.4"
}

// read the version from the gradle.properties file
val versionName: String by project
val groupName: String by project
group = groupName
version = versionName

dependencies {
  implementation(project(":base"))
  implementation(project(":common"))
  implementation(libs.com.tencent.tars.core)
  implementation(libs.io.exoquery.pprint)
  implementation(libs.io.ktor.client.core)
  implementation(libs.io.ktor.serialization.kotlinx.json)
  implementation(libs.com.google.protobuf.javalite)
  implementation(libs.com.google.protobuf.protobuf.java.util)
  implementation(libs.com.google.protobuf.protobuf.kotlin.lite)
  implementation(libs.com.michael.bull.kotlin.result)
  implementation(libs.org.jetbrains.kotlinx.datetime)
  implementation(libs.org.jetbrains.kotlinx.atomicfu)
  implementation(libs.org.openjdk.nashorn.nashorn.core)
  testImplementation(libs.bundles.test.jvm)
}

tasks.test {
  useJUnitPlatform()
}

configurations.all {
  // com tencent tars causes a conflict with the logback-classic version, so we force the version
  resolutionStrategy {
    force(libs.ch.qos.logback.classic.get())
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
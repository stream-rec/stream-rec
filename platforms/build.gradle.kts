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
  implementation("com.tencent.tars:tars-core:1.7.3")
  implementation(libs.io.exoquery.pprint)
  implementation(libs.io.ktor.client.core)
  implementation(libs.io.ktor.serialization.kotlinx.json)
  implementation(libs.com.google.protobuf.javalite)
  implementation(libs.com.google.protobuf.protobuf.java.util)
  implementation(libs.com.google.protobuf.protobuf.kotlin.lite)
  implementation(libs.org.jetbrains.kotlinx.datetime)
  implementation(libs.org.openjdk.nashorn.nashorn.core)
  testImplementation(libs.bundles.test.jvm)
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
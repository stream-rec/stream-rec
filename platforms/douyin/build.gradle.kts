plugins {
  alias(libs.plugins.kotlin.jvm)
  //  id("com.google.protobuf") version "0.9.4"
}

group = "github.hua0512.streamrec"
version = "0.5.0"

dependencies {
  implementation(project(":base"))
  implementation(libs.com.google.protobuf.javalite)
  implementation(libs.com.google.protobuf.protobuf.java.util)
  implementation(libs.com.google.protobuf.protobuf.kotlin.lite)
  implementation(libs.org.jetbrains.kotlinx.datetime)
  testImplementation(libs.bundles.test.jvm)
}

tasks.test {
  useJUnitPlatform()
}
kotlin {
  jvmToolchain(17)
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
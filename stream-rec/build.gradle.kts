import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  alias(libs.plugins.kotlin.jvm)
  kotlin("plugin.serialization") version "1.9.22"
  id("com.google.devtools.ksp") version "1.9.22-1.0.17"
  alias(libs.plugins.ktor)
  id("app.cash.sqldelight") version "2.0.1"
  //  id("com.google.protobuf") version "0.9.4"
}

group = "github.hua0512"
version = "0.3.1"

application.mainClass.set("github.hua0512.Application")

ktor {
  fatJar {
    archiveFileName.set("stream-rec.jar")
  }
}

sqldelight {
  databases {
    create("StreamRecDatabase") {
      packageName.set("github.hua0512")
    }
  }
}


dependencies {
  implementation(libs.ch.qos.logback.classic)
  implementation(libs.io.ktor.client.core)
  implementation(libs.io.ktor.client.okhttp)
  implementation(libs.io.ktor.serialization.kotlinx.json)
  implementation(libs.io.ktor.client.logging)
  implementation(libs.org.jetbrains.kotlinx.coroutines.core)

  implementation(libs.org.jetbrains.kotlinx.serialization.json)
  implementation(libs.com.google.dagger.dagger)
  implementation("com.tencent.tars:tars-core:1.7.3")
  implementation("net.peanuuutz.tomlkt:tomlkt:0.3.7")
  implementation("me.tongfei:progressbar:0.10.0")
  implementation("com.google.protobuf:protobuf-java-util:3.25.2")
  implementation("com.google.protobuf:protobuf-javalite:3.25.2")
  implementation("com.google.protobuf:protobuf-kotlin-lite:3.25.2")
  implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0")

  implementation("app.cash.sqldelight:sqlite-driver:2.0.1")
  implementation("app.cash.sqldelight:coroutines-extensions:2.0.1")
  implementation("app.cash.sqldelight:primitive-adapters:2.0.1")

//  implementation("org.mongodb:bson-kotlinx:4.11.0")


  ksp(libs.com.google.dagger.dagger.compiler)
  testImplementation("org.jetbrains.kotlin:kotlin-test")
  testImplementation(libs.org.jetbrains.kotlinx.coroutines.debug)
  testImplementation(libs.org.jetbrains.kotlinx.coroutines.test)
  testImplementation("org.mockito:mockito-core:3.12.4")
  testImplementation("org.junit.jupiter:junit-jupiter:5.8.2")
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

tasks.test {
  useJUnitPlatform()
}
kotlin {
  jvmToolchain(17)
}


// Workaround for https://github.com/google/dagger/issues/4158
kotlin {
  afterEvaluate {
    tasks.getByName<KotlinCompile>("kspKotlin") {
      setSource(tasks.getByName("generateMainStreamRecDatabaseInterface").outputs)
    }
  }
}
plugins {
  alias(libs.plugins.kotlin.jvm)
}

group = "github.hua0512.streamrec"
version = "0.5.0"

dependencies {
  implementation(project(":base"))
  implementation("com.tencent.tars:tars-core:1.7.3")
  implementation(libs.org.jetbrains.kotlinx.datetime)
  testImplementation(libs.bundles.test.jvm)
}

tasks.test {
  useJUnitPlatform()
}
kotlin {
  jvmToolchain(17)
}
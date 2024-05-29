import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_0

plugins {
  alias(libs.plugins.kotlin.jvm)
}


kotlin {
  compilerOptions {
    freeCompilerArgs.addAll(
      "-Xno-call-assertions",
      "-Xno-param-assertions",
      "-Xno-receiver-assertions",
      "-opt-in=kotlin.RequiresOptIn",
      "-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
      "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi"
    )
    progressiveMode = true
    apiVersion.set(KOTLIN_2_0)
    jvmTarget.set(JvmTarget.JVM_21)
  }
}


tasks.withType<JavaCompile>().configureEach {
  targetCompatibility = JavaVersion.VERSION_21.toString()
  sourceCompatibility = JavaVersion.VERSION_11.toString()
  options.isFork = true
}
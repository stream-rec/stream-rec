import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  alias(libs.plugins.kotlin.jvm)
}

allprojects {
  tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
      val arguments = mutableListOf<String>()
      // https://kotlinlang.org/docs/compiler-reference.html#progressive
      arguments += "-progressive"
      // Generate smaller bytecode by not generating runtime not-null assertions.
      arguments += "-Xno-call-assertions"
      arguments += "-Xno-param-assertions"
      arguments += "-Xno-receiver-assertions"
      arguments += "-opt-in=kotlin.RequiresOptIn"
      freeCompilerArgs.addAll(arguments)
    }
    kotlinOptions {
      jvmTarget = JavaVersion.VERSION_17.toString()
    }
  }
  tasks.withType<JavaCompile>().configureEach {
    options.isFork = true
  }
}

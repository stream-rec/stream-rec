package github.hua0512.plugins.command

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.asErr
import github.hua0512.data.plugin.ItemExecutionTiming
import github.hua0512.data.plugin.PluginConfigs.SimpleShellCommandConfig
import github.hua0512.data.dto.IOutputFile
import github.hua0512.utils.isWindows


/**
 * A simple plugin that executes shell commands on input files.
 */
class SimpleShellCommandPlugin(
  config: SimpleShellCommandConfig,
  override val id: String = "shell-command",
  override val name: String = "Shell Command",
  override val description: String = "Executes shell commands on input files",
  override val version: String = "1.0.0",
  override val author: String = "System",
) : BaseCommandPlugin<SimpleShellCommandConfig>(config) {

  override val commandType: String = "shell-command"

  override val batchMode: Boolean = config.includeAllInputs

  override fun buildCommand(inputs: List<IOutputFile>): List<String> {
    val command = mutableListOf<String>()

    // Determine if we need to use a shell
    if (config.useShell) {
      val isWindows = isWindows()
      if (isWindows) {
        command.add("cmd.exe")
        command.add("/c")
      } else {
        command.add("/bin/sh")
        command.add("-c")
      }

      // For shell execution, prepare the complete command as a single string
      val shellCommand = buildShellCommandString(inputs)
      command.add(shellCommand)
    } else {
      // Direct command execution
      // Start with the base command
      // TODO: Add support for BATCH processes
      command.add(config.commandTemplate.replacePlaceholders(inputs.first()))

      // TODO: Add support for BATCH processes
      // Add configured arguments
      command.addAll(config.arguments.map { it.replacePlaceholders(inputs.first()) })

      // Add input files as arguments if configured
      if (config.includeInputPath) {
        if (config.includeAllInputs) {
          // Add all input files as arguments
          command.add(inputs.joinToString("\n") { it.path })
        } else if (inputs.isNotEmpty()) {
          // Add just the first input file
          command.add(inputs.first().path)
        }
      }
    }

    return command
  }

  private fun buildShellCommandString(inputs: List<IOutputFile>): String {
    var cmd = config.commandTemplate

    // Replace {input} with the first input file path
    if (inputs.isNotEmpty()) {
      cmd = cmd.replace("{input}", inputs.first().path)
    }

    // Replace {inputs} with all input file paths
    if (config.includeAllInputs) {
      val inputPaths = inputs.joinToString("\n") { it.path }
      cmd = cmd.replace("{inputs}", inputPaths)
    }

    // Add arguments if any
    if (config.arguments.isNotEmpty()) {
      cmd += " " + config.arguments.joinToString(" ")
    }

    // TODO: Add support for BATCH processes
    return cmd.replacePlaceholders(inputs.first())
  }

  override fun processCommandResult(
    result: CommandResult,
    inputs: List<IOutputFile>,
  ): List<IOutputFile> {
    // If no output files were generated, return the original inputs
    if (result.generatedFiles.isEmpty()) {
      return inputs
    }

    // Convert generated files to IOutputFile objects
    // TODO : Uncomment and implement this part
//    val outputFiles = result.generatedFiles.map { path ->
//      object : IOutputFile {
//        override var path: String = path.toString()
//        override var size: Long = path.toFile().length()
//      }
//    }

    // Return both original inputs and generated files
//    return inputs + outputFiles
    return inputs
  }

  /**
   * Override to provide more specific validation for shell commands.
   */
  override fun validateCommand(command: List<String>): Result<Unit, CommandError> {
    val baseResult = super.validateCommand(command)
    if (baseResult.isErr) {
      return baseResult.asErr()
    }

    // Additional shell-specific security checks
    if (config.useShell) {
      val shellCommand = command.last()

      // Check for suspicious shell commands
      val suspiciousPatterns = listOf(
        "rm -rf /",
        "deltree",
        "format",
        "> /dev/sda",
        ":(){:|:&};:",   // Fork bomb
        "dd if=/dev/random"
      )

      for (pattern in suspiciousPatterns) {
        if (shellCommand.contains(pattern)) {
          return com.github.michaelbull.result.Err(
            CommandError.SecurityError(
              "Potentially dangerous command detected: $shellCommand"
            )
          )
        }
      }
    }

    return Ok(Unit)
  }

  /**
   * Provide detailed information about command execution.
   */
  override suspend fun onItemSuccess(
    input: IOutputFile,
    outputs: List<IOutputFile>,
    timing: ItemExecutionTiming,
  ) {
    val newFiles = outputs.filter { output -> output.path != input.path }

    println("Command executed on ${input.path} in ${timing.duration}")
    if (newFiles.isNotEmpty()) {
      println("Generated ${newFiles.size} new files:")
      newFiles.forEach { output ->
        println("  - ${output.path} (${output.size} bytes)")
      }
    }
  }
}

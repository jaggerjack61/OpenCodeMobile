package com.opencode.mobile.network

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

class ShellTool(context: Context) {
    private val workspace = File(context.filesDir, "workspace").apply { mkdirs() }
    private val temp = File(workspace, "tmp").apply { mkdirs() }

    suspend fun run(command: String): String = withContext(Dispatchers.IO) {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) return@withContext "Shell error: empty command."
        if (trimmed.length > MAX_COMMAND_LENGTH) {
            return@withContext "Shell error: command is longer than $MAX_COMMAND_LENGTH characters."
        }

        val process = ProcessBuilder("/system/bin/sh", "-c", trimmed)
            .directory(workspace)
            .redirectErrorStream(true)
            .apply {
                environment()["HOME"] = workspace.absolutePath
                environment()["TMPDIR"] = temp.absolutePath
            }
            .start()

        val output = StringBuilder()
        var truncated = false
        val reader = Thread {
            runCatching {
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        if (output.length < MAX_OUTPUT_CHARS) {
                            output.append(line).append('\n')
                        } else {
                            truncated = true
                        }
                    }
                }
            }
        }.apply {
            isDaemon = true
            start()
        }

        val finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            process.waitFor(2, TimeUnit.SECONDS)
        }
        reader.join(1_000)

        val exit = if (finished) runCatching { process.exitValue() }.getOrDefault(-1) else -1
        val header = when {
            !finished -> "Command timed out after $TIMEOUT_SECONDS seconds."
            exit == 0 -> "Command completed successfully."
            else -> "Command exited with code $exit."
        }
        buildString {
            append(header)
            append("\nWorking directory: ").append(workspace.absolutePath)
            append("\n\n")
            append(output.toString().trimEnd())
            if (truncated) append("\n[output truncated]")
        }.take(MAX_OUTPUT_CHARS + 500)
    }

    companion object {
        private const val TIMEOUT_SECONDS = 20L
        private const val MAX_OUTPUT_CHARS = 16_000
        private const val MAX_COMMAND_LENGTH = 4_000
    }
}

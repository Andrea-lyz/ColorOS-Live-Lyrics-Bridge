package io.github.andrealtb.lockscreenlyrics.management.root

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Root is used by the ordinary App process only, and only to read the LSPosed configuration
 * through its official CLI and to open the LSPosed manager on a module (plan §14.10). It never
 * writes framework configuration and never runs from Hook/bootstrap/render code.
 */
internal enum class RootState {
    /** The user has not asked for root yet; nothing has been run. */
    NOT_REQUESTED,
    CHECKING,
    GRANTED,
    /** su exists but refused (denied in Magisk/KernelSU/APatch, or timed out). */
    DENIED,
    /** No su binary is visible to this app. */
    UNAVAILABLE,
}

internal class ShellResult(val exitCode: Int, val stdout: String)

internal object RootShell {
    /** Runs [script] with `su -c`; null when su cannot be started (not installed). */
    fun run(script: String, timeoutMs: Long, maxBytes: Int = 256 * 1024): ShellResult? {
        val process = try {
            ProcessBuilder("su", "-c", script).redirectErrorStream(false).start()
        } catch (_: IOException) {
            return null
        }
        val out = ByteArrayOutputStream()
        val reader = Thread {
            try {
                process.inputStream.use { input ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (out.size() + read > maxBytes) break
                        synchronized(out) { out.write(buffer, 0, read) }
                    }
                }
            } catch (_: IOException) {
            }
        }.apply { isDaemon = true; start() }
        // stderr is drained and dropped: it can name paths and is never shown or logged.
        Thread {
            try {
                process.errorStream.use { err -> val sink = ByteArray(4096); while (err.read(sink) >= 0) Unit }
            } catch (_: IOException) {
            }
        }.apply { isDaemon = true; start() }
        val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        if (!finished) {
            process.destroyForcibly()
            return ShellResult(-1, "")
        }
        reader.join(1000)
        return ShellResult(process.exitValue(), synchronized(out) { out.toString(Charsets.UTF_8.name()) })
    }
}

/** Copies one root-only file into [target] with `su -c cat`; false when it is missing or too large. */
internal fun RootShell.copyFile(path: String, target: java.io.File, timeoutMs: Long, maxBytes: Long): Boolean {
    require(path.matches(Regex("^/[A-Za-z0-9_./-]+$"))) { "unexpected path" }
    val process = try {
        ProcessBuilder("su", "-c", "cat '$path'").start()
    } catch (_: IOException) {
        return false
    }
    Thread {
        try {
            process.errorStream.use { err -> val sink = ByteArray(4096); while (err.read(sink) >= 0) Unit }
        } catch (_: IOException) {
        }
    }.apply { isDaemon = true; start() }
    var total = 0L
    var tooLarge = false
    val copier = Thread {
        try {
            process.inputStream.use { input ->
                target.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > maxBytes) {
                            tooLarge = true
                            break
                        }
                        out.write(buffer, 0, read)
                    }
                }
            }
        } catch (_: IOException) {
        }
    }.apply { isDaemon = true; start() }
    if (!process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
        process.destroyForcibly()
        target.delete()
        return false
    }
    copier.join(timeoutMs)
    if (tooLarge || process.exitValue() != 0) {
        target.delete()
        return false
    }
    return true
}

/** Remembers whether the user asked for root; checks never prompt unless asked. */
internal object RootAccess {
    private const val PREFS = "provider_store"
    private const val KEY_REQUESTED = "root_requested"
    private const val PROMPT_TIMEOUT_MS = 30_000L
    private const val SILENT_TIMEOUT_MS = 10_000L

    fun requested(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_REQUESTED, false)

    /** Blocking. [prompt] is true only for the user's own tap (the root manager may ask them). */
    fun check(context: Context, prompt: Boolean, previouslyGranted: Boolean = false): RootState {
        if (!prompt && !requested(context)) return RootState.NOT_REQUESTED
        if (prompt) {
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_REQUESTED, true).apply()
        }
        if (prompt) return once(PROMPT_TIMEOUT_MS)
        // A single silent check can fail while the root manager is busy or still starting (seen
        // on PJZ110 right after returning from the LSPosed manager): retry before reporting a
        // loss, and never downgrade a grant this screen already had because of timeouts alone.
        var last = RootState.DENIED
        var timedOutOnly = true
        repeat(SILENT_ATTEMPTS) { attempt ->
            if (attempt > 0) Thread.sleep(SILENT_RETRY_DELAY_MS)
            val result = RootShell.run("id -u", SILENT_TIMEOUT_MS) ?: return RootState.UNAVAILABLE
            last = rootStateOf(result.exitCode, result.stdout)
            if (last == RootState.GRANTED) return last
            if (result.exitCode != TIMED_OUT) timedOutOnly = false
        }
        return if (timedOutOnly && previouslyGranted) RootState.GRANTED else last
    }

    private fun once(timeoutMs: Long): RootState {
        val result = RootShell.run("id -u", timeoutMs) ?: return RootState.UNAVAILABLE
        return rootStateOf(result.exitCode, result.stdout)
    }

    private const val SILENT_ATTEMPTS = 3
    private const val SILENT_RETRY_DELAY_MS = 1_000L
    private const val TIMED_OUT = -1
}

/** Pure: `id -u` printed 0 and exited 0. */
internal fun rootStateOf(exitCode: Int, stdout: String): RootState =
    if (exitCode == 0 && stdout.trim() == "0") RootState.GRANTED else RootState.DENIED

package slop.miho.chumpy.windows

import java.io.File
import java.util.concurrent.TimeUnit

/** Starts native cores through hidden PowerShell and tracks the child PID for reliable shutdown. */
internal class ManagedEngine {
    @Volatile private var processId: Long? = null

    @Synchronized fun start(executable: File, config: File, tun: Boolean) {
        require(executable.isFile) { "Движок не найден: ${executable.path}" }
        require(config.isFile) { "JSON не найден: ${config.path}" }
        val previous = processId
        if (previous != null && isProcessRunning(previous)) error("Движок уже запущен")
        processId = null
        if (tun) require(isAdministrator()) { "Для TUN запустите Chumpy от имени администратора." }

        val logDir = File(config.parentFile, "logs").apply { mkdirs() }
        val stdout = File(logDir, "engine-out.log")
        val stderr = File(logDir, "engine-error.log")
        val script = """
            ${'$'}ErrorActionPreference = 'Stop'
            ${'$'}p = Start-Process -FilePath ${'$'}env:CHUMPY_ENGINE_EXE -ArgumentList @('run','-c', ('"' + ${'$'}env:CHUMPY_ENGINE_CONFIG + '"')) -WorkingDirectory ${'$'}env:CHUMPY_ENGINE_DIR -WindowStyle Hidden -PassThru -RedirectStandardOutput ${'$'}env:CHUMPY_ENGINE_STDOUT -RedirectStandardError ${'$'}env:CHUMPY_ENGINE_STDERR
            [Console]::WriteLine(${'$'}p.Id)
        """.trimIndent()
        val encoded = java.util.Base64.getEncoder().encodeToString(script.toByteArray(Charsets.UTF_16LE))
        val launcher = ProcessBuilder(
            "powershell.exe", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden", "-ExecutionPolicy", "Bypass", "-EncodedCommand", encoded
        ).redirectErrorStream(true)
        launcher.environment().apply {
            put("CHUMPY_ENGINE_EXE", executable.absolutePath)
            put("CHUMPY_ENGINE_CONFIG", config.absolutePath)
            put("CHUMPY_ENGINE_DIR", executable.parentFile.absolutePath)
            put("CHUMPY_ENGINE_STDOUT", stdout.absolutePath)
            put("CHUMPY_ENGINE_STDERR", stderr.absolutePath)
        }
        val result = launcher.start()
        check(result.waitFor(15, TimeUnit.SECONDS)) { "Тайм-аут запуска PowerShell." }
        val output = result.inputStream.bufferedReader().readText().trim()
        check(result.exitValue() == 0) { "Не удалось запустить движок скрыто: $output" }
        val pid = output.lineSequence().lastOrNull()?.trim()?.toLongOrNull()
            ?: error("PowerShell не вернул PID процесса Xray: $output")
        processId = pid
        Thread.sleep(700)
        check(isProcessRunning(pid)) {
            processId = null
            "Движок завершился при запуске. Логи: ${stdout.absolutePath} и ${stderr.absolutePath}"
        }
    }

    @Synchronized fun stop() {
        val pid = processId ?: return
        runCatching {
            ProcessBuilder("taskkill.exe", "/PID", pid.toString(), "/T", "/F")
                .redirectErrorStream(true).start().waitFor(5, TimeUnit.SECONDS)
        }
        processId = null
    }

    private fun isProcessRunning(pid: Long): Boolean {
        val check = ProcessBuilder(
            "powershell.exe", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden", "-Command",
            "if (Get-Process -Id $pid -ErrorAction SilentlyContinue) { exit 0 } else { exit 1 }"
        ).start()
        return check.waitFor(5, TimeUnit.SECONDS) && check.exitValue() == 0
    }

    private fun isAdministrator(): Boolean {
        val p = ProcessBuilder("net", "session").redirectErrorStream(true).start()
        p.inputStream.close()
        return p.waitFor() == 0
    }
}

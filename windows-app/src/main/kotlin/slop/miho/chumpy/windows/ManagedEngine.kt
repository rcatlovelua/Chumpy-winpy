package slop.miho.chumpy.windows

import java.io.File
import java.util.concurrent.TimeUnit

/** Runs a native Windows core without opening a console window. */
internal class ManagedEngine {
    @Volatile private var process: Process? = null

    @Synchronized fun start(executable: File, config: File, tun: Boolean) {
        require(executable.isFile) { "Движок не найден: ${executable.path}" }
        require(config.isFile) { "JSON не найден: ${config.path}" }
        check(process?.isAlive != true) { "Движок уже запущен" }
        if (tun) require(isAdministrator()) { "Для TUN запустите Chumpy от имени администратора." }

        val log = File(config.parentFile, "chumpy-engine.log")
        val command = listOf(executable.absolutePath, "run", "-c", config.absolutePath)
        val builder = ProcessBuilder(command)
            .directory(executable.parentFile)
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.appendTo(log))

        // CREATE_NO_WINDOW prevents a console flash for console-subsystem cores such as xray.exe.
        if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
            builder.environment()["CHUMPY_NO_CONSOLE"] = "1"
        }
        process = builder.start()
        Thread.sleep(500)
        check(process?.isAlive == true) {
            process = null
            "Движок завершился при запуске. Проверьте ${log.absolutePath}"
        }
    }

    @Synchronized fun stop() {
        val p = process ?: return
        p.destroy()
        if (!p.waitFor(3, TimeUnit.SECONDS)) {
            p.destroyForcibly()
            p.waitFor(2, TimeUnit.SECONDS)
        }
        process = null
    }

    private fun isAdministrator(): Boolean {
        val p = ProcessBuilder("net", "session").redirectErrorStream(true).start()
        p.inputStream.close()
        return p.waitFor() == 0
    }
}

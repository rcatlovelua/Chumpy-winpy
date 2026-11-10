package slop.miho.chumpy.windows

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Installs the Xray binary bundled by the Windows CI build into the user's app data directory. */
internal object BundledXray {
    fun install(home: File): File? {
        val resource = BundledXray::class.java.getResourceAsStream("/native/windows/xray.exe") ?: return null
        val executable = File(home, "bin/xray.exe")
        executable.parentFile.mkdirs()
        resource.use { input ->
            val temporary = File(executable.parentFile, "xray.exe.new")
            Files.copy(input, temporary.toPath(), StandardCopyOption.REPLACE_EXISTING)
            try {
                Files.move(
                    temporary.toPath(),
                    executable.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
                )
            } catch (_: Exception) {
                Files.move(temporary.toPath(), executable.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }
        return executable.takeIf { it.isFile }
    }
}

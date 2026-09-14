package app.askya.desktop

import java.io.File

/**
 * «Запускать вместе с Windows»: Askya открывается при входе в систему — сразу
 * к часам, без окна, — и напоминания звонят весь день, даже если окно ни разу
 * не открывали (см. «Напоминания» в README).
 *
 * Запись — в `Run` раздела пользователя в реестре, как у любой программы из
 * «Автозагрузки» диспетчера задач. Там её видно и там же её можно выключить;
 * прав администратора она не требует и живёт только у этого пользователя.
 *
 * Выключенное в диспетчере задач Windows помечает отдельно
 * (`StartupApproved`), саму запись не трогая. Поэтому «включено» — это
 * запись есть и не помечена выключенной, а включение снимает и пометку:
 * иначе переключатель в Askya стоял бы, а Askya не запускалась.
 */
object DesktopAutostart {

    /**
     * Askya.exe, которая сейчас работает, — её и запускать. Путь оставляет
     * установщик (`jpackage.app-path`); из-под Gradle его нет, и строки в
     * настройках тогда нет тоже: запускать при входе было бы нечего.
     */
    val launcher: File? = System.getProperty("jpackage.app-path")?.let(::File)?.takeIf(File::isFile)

    fun isOn(): Boolean {
        if (query(RUN) == null) return false
        // «02 00 00 …» — включено, «03 00 00 …» — выключено в диспетчере задач.
        val approved = query(APPROVED)?.substringAfter("REG_BINARY")?.trim()?.take(2)
        return approved?.toIntOrNull(16)?.let { it % 2 == 0 } ?: true
    }

    fun turnOn() {
        val exe = checkNotNull(launcher) { "не видно Askya.exe" }
        // Через файл .reg, а не `reg add`: путь нужен в кавычках — в нём
        // бывают пробелы, — а кавычки внутри аргумента Java передаёт reg.exe
        // как придётся. В файле они экранированы по его правилам и доходят
        // как есть.
        val command = "\"${exe.absolutePath}\" $AT_LOGIN"
        val file = File.createTempFile("askya-autostart", ".reg")
        try {
            file.writeText(
                Char(0xFEFF) + "Windows Registry Editor Version 5.00\r\n\r\n" +
                    "[$RUN]\r\n\"$NAME\"=\"${command.replace("\\", "\\\\").replace("\"", "\\\"")}\"\r\n",
                Charsets.UTF_16LE,
            )
            reg("import", file.absolutePath)
        } finally {
            file.delete()
        }
        runCatching { reg("delete", APPROVED, "/v", NAME, "/f") }
    }

    fun turnOff() {
        if (query(RUN) != null) reg("delete", RUN, "/v", NAME, "/f")
        runCatching { reg("delete", APPROVED, "/v", NAME, "/f") }
    }

    /** Строка значения из `reg query`; `null` — значения нет. */
    private fun query(key: String): String? {
        val process = ProcessBuilder("reg", "query", key, "/v", NAME).redirectErrorStream(true).start()
        val out = process.inputStream.bufferedReader().readText()
        if (process.waitFor() != 0) return null
        return out.lineSequence().firstOrNull { it.trim().startsWith(NAME) }
    }

    /**
     * Ответ reg.exe не пересказывается: он в кодовой странице консоли, а её
     * у Java, которую везёт Askya.exe, нет — вышли бы кракозябры.
     */
    private fun reg(vararg args: String) {
        val process = ProcessBuilder(listOf("reg") + args).redirectErrorStream(true).start()
        process.inputStream.readBytes()
        check(process.waitFor() == 0) { "Windows не дала записать автозапуск" }
    }

    /** Ключ запуска: с ним Askya открывается без окна, сразу к часам. */
    const val AT_LOGIN = "--tray"

    private const val NAME = "Askya"
    private const val RUN = "HKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\CurrentVersion\\Run"
    private const val APPROVED =
        "HKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\StartupApproved\\Run"
}

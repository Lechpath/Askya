package app.askya.data.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Облако, каким его видит синхронизация: немой почтовый ящик.
 *
 * Четыре действия — перечислить, взять, положить, убрать, — и ни одного слова
 * про Google, замки, учётные записи и слияние. Так и задумано: складывать
 * правки умеет Askya, а от хранилища нужно только, чтобы оно не теряло файлы.
 *
 * Отсюда и порядок этапов. Google Диск ([app.askya.data.sync] дальше) — это
 * одна реализация этого лица; вторая, [FolderStore], — обычная папка, и на ней
 * слияние проверяется целиком на одном компьютере, без сети, без ключей Google
 * и без ожидания чужого сервера. Если Диск когда-нибудь станет неудобен, менять
 * придётся только это место.
 *
 * Пути — со слэшем, от корня папки Askya в облаке: `devices/7f3a…/000041.pack`.
 */
interface SyncStore {

    /** Что лежит внутри папки: имена, не пути. Нет папки — пусто. */
    suspend fun list(folder: String): List<String>

    /** Байты файла или `null`, если его нет. */
    suspend fun read(path: String): ByteArray?

    /** Положить, создав папки по пути. Файл под тем же именем заменяется. */
    suspend fun write(path: String, bytes: ByteArray)

    suspend fun remove(path: String)
}

/**
 * Облако как обычная папка.
 *
 * Ею проверяется всё, кроме сети: две Askya с разными папками данных сходятся
 * через третью папку на том же компьютере — и это ровно то же слияние, какое
 * потом пойдёт через Диск. Годится она и сама по себе: общая папка на домашнем
 * диске или сетевом хранилище — такое же облако для того, кто не хочет чужого.
 */
class FolderStore(private val root: File) : SyncStore {

    override suspend fun list(folder: String): List<String> = withContext(Dispatchers.IO) {
        File(root, folder).list()?.sorted().orEmpty()
    }

    override suspend fun read(path: String): ByteArray? = withContext(Dispatchers.IO) {
        File(root, path).takeIf { it.isFile }?.readBytes()
    }

    override suspend fun write(path: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        val file = File(root, path)
        file.parentFile?.mkdirs()
        // Через временный файл: иначе оборванная запись оставила бы порцию
        // наполовину, и другое устройство прочло бы огрызок. Порции пишутся
        // раз и не правятся, но оборваться может и первая запись.
        val half = File(file.parentFile, file.name + ".half")
        half.writeBytes(bytes)
        if (file.exists()) file.delete()
        check(half.renameTo(file)) { "не переименовался: " + file.path }
    }

    override suspend fun remove(path: String) {
        withContext(Dispatchers.IO) { File(root, path).delete() }
    }
}

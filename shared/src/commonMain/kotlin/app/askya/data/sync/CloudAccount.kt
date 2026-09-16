package app.askya.data.sync

import app.askya.domain.account.Secrets

/**
 * Замок, которым заперт ключ данных: соль, число витков и сам запертый ключ.
 *
 * Замков два — паролем и кодом восстановления, — и оба открывают один и тот же
 * ключ данных. Забытый пароль поэтому не отрезает от облака: код с бумажки
 * открывает записи и позволяет поставить новый пароль.
 */
data class CloudLock(val salt: String, val rounds: Int, val key: String)

/**
 * `account.json` — единственное, что лежит в облаке открытым текстом.
 *
 * Открытым, потому что иначе нечем было бы открыть всё остальное: здесь лежат
 * соль и число витков, из которых считается ключ из пароля, и ключ данных,
 * этим ключом запертый. Самого пароля здесь нет, и подобрать его по этому
 * файлу — то же самое, что подбирать AES-256.
 *
 * Отсюда же берётся проверка пароля на новом устройстве: если ключ данных
 * открылся, пароль тот. Спрашивать не у кого — сервера у Askya нет.
 *
 * Номер аккаунта [id] — не имя человека и не почта, а случайные шестнадцать
 * байт: облако не должно знать, чей это аккаунт, даже если файл кто-то увидит.
 */
data class CloudAccount(
    val id: String,
    val password: CloudLock,
    /** Замок кода восстановления. `null` — код не заводили. */
    val recovery: CloudLock?,
) {

    /** Открыть ключ данных паролем. `null` — пароль не тот. */
    fun open(secret: String): ByteArray? = unlock(secret, password, PASSWORD)

    /**
     * Открыть кодом восстановления — тем, что переписан с бумажки: дефисы и
     * регистр приводятся здесь же, как и на экране входа.
     */
    fun openRecovered(code: String): ByteArray? =
        recovery?.let { unlock(Secrets.normalizeCode(code), it, RECOVERY) }

    /**
     * Новый пароль. Перезапирается только ключ данных: порции и файлы,
     * лежащие в облаке, не трогаются вовсе — их ключ не менялся.
     */
    fun withPassword(dataKey: ByteArray, secret: String, rounds: Int = Crypt.ROUNDS): CloudAccount =
        copy(password = lock(dataKey, secret, rounds, PASSWORD))

    fun withRecovery(dataKey: ByteArray, code: String, rounds: Int = Crypt.ROUNDS): CloudAccount =
        copy(recovery = lock(dataKey, Secrets.normalizeCode(code), rounds, RECOVERY))

    fun json(): String = Json.write(
        linkedMapOf(
            "account" to FORMAT,
            "id" to id,
            "password" to password.map(),
            "recovery" to recovery?.map(),
        ),
    )

    private fun CloudLock.map() = linkedMapOf("salt" to salt, "rounds" to rounds, "key" to key)

    companion object {

        /** Где файл лежит в облаке. */
        const val PATH = "account.json"

        /** Версия самого файла: по ней читают его будущие сборки. */
        const val FORMAT = 1

        /**
         * Подпись, которой накрыт запертый ключ. Разная у двух замков: иначе
         * запертое паролем можно было бы переложить в поле кода восстановления
         * и наоборот.
         */
        private const val PASSWORD = "account.json#password"
        private const val RECOVERY = "account.json#recovery"

        /**
         * Завести аккаунт в облаке. Ключ данных заводится здесь и отдаётся
         * наружу: тот, кто подключает облако, кладёт его себе и больше нигде
         * не берёт.
         */
        fun made(
            password: String,
            recovery: String?,
            rounds: Int = Crypt.ROUNDS,
        ): Pair<CloudAccount, ByteArray> {
            val dataKey = Crypt.key()
            val account = CloudAccount(
                id = Uid.new(),
                password = lock(dataKey, password, rounds, PASSWORD),
                recovery = recovery?.let { lock(dataKey, Secrets.normalizeCode(it), rounds, RECOVERY) },
            )
            return account to dataKey
        }

        /** Прочитать файл. `null` — испорчен или от версии, которой ещё нет. */
        fun of(text: String): CloudAccount? = runCatching {
            val map = Json.read(text) as Map<*, *>
            if ((map["account"] as? Long)?.toInt() != FORMAT) return null
            CloudAccount(
                id = map["id"] as String,
                password = lockOf(map["password"]) ?: return null,
                recovery = lockOf(map["recovery"]),
            )
        }.getOrNull()

        private fun lockOf(raw: Any?): CloudLock? {
            val map = raw as? Map<*, *> ?: return null
            return CloudLock(
                salt = map["salt"] as? String ?: return null,
                rounds = (map["rounds"] as? Long)?.toInt() ?: return null,
                key = map["key"] as? String ?: return null,
            )
        }

        private fun lock(dataKey: ByteArray, secret: String, rounds: Int, label: String): CloudLock {
            val salt = Crypt.salt()
            val fromSecret = Crypt.fromSecret(secret, salt, rounds)
            return CloudLock(
                salt = Crypt.b64(salt),
                rounds = rounds,
                key = Crypt.b64(Crypt.seal(fromSecret, dataKey, label)),
            )
        }

        private fun unlock(secret: String, lock: CloudLock, label: String): ByteArray? = runCatching {
            val fromSecret = Crypt.fromSecret(secret, Crypt.unb64(lock.salt), lock.rounds)
            Crypt.open(fromSecret, Crypt.unb64(lock.key), label)
        }.getOrNull()
    }
}

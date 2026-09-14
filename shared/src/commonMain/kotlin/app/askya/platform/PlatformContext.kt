package app.askya.platform

import androidx.compose.runtime.ProvidableCompositionLocal
import java.io.InputStream

/**
 * То, через что общий код просит систему о чём-то: открыть файл по ссылке,
 * показать строчку внизу, отдать адрес браузеру.
 *
 * На телефоне это сам `Context` — тот же, что был у экранов до переезда, и
 * потому переезд почти не тронул их кода: `LocalContext.current` стал
 * [LocalPlatformContext]`.current`, а всё, что с ним делали, осталось. На
 * компьютере контекста у системы нет, и здесь стоит пустой объект-заместитель:
 * всё нужное Windows-версия знает сама.
 */
expect abstract class PlatformContext

/** Контекст для экранов — тот же, что `LocalContext` у телефона. */
expect val LocalPlatformContext: ProvidableCompositionLocal<PlatformContext>

/**
 * Файл по ссылке, как её хранит запись.
 *
 * На телефоне ссылка — `content://` или `file://`, и открывает её система. На
 * компьютере — `file:/…`, и открывается она обычным файлом. `null` — файла
 * нет или его нечем открыть; запись от этого не падает.
 */
expect fun PlatformContext.openStream(uri: String): InputStream?

/**
 * Строчка внизу экрана — короткое «не вышло» или «готово».
 *
 * На телефоне это системный тост. На компьютере — такая же строчка поверх окна
 * (см. `Notices` в Windows-версии): система сама таких не показывает.
 */
expect fun PlatformContext.toast(text: String)

package app.askya.ui.scroll

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.askya.app.appContainer
import app.askya.ui.components.ScreenScaffold
import app.askya.ui.theme.Accent
import app.askya.ui.theme.AccentSoft
import app.askya.ui.theme.Cream
import app.askya.ui.theme.Ink
import app.askya.ui.theme.cardEdge

/**
 * Карточка картинки: подпись сверху, сама картинка посередине, альбом снизу.
 *
 * Открывается сразу после загрузки и потом из просмотра — «Переименовать». Это
 * одно и то же место: подписать только что добавленную картинку и переписать
 * подпись у давно лежащей — одно действие, и разводить его на два экрана
 * значило бы спрашивать одно и то же двумя способами. Подпись меняется вместе
 * с именем файла в папке Askya (см. `NoteRepository.captionImage`).
 *
 * Раньше картинка попадала в тот же разговор, что и заметка: «Как назовём?» —
 * «В какую книгу?». Разговор задаёт по вопросу на экран и картинку при этом не
 * показывает, а подписывают именно то, что видно. Здесь всё сразу и над самим
 * снимком: имя файла в поле уже стоит, и если подпись не нужна, остаётся
 * нажать «Готово».
 *
 * Миниатюра примерно в половину экрана и повторяет картинку формой: у
 * вертикального снимка карточка стоячая, у горизонтального — лежачая.
 * Квадратная рамка на всё резала бы вертикальные снимки, а по обрезанному
 * куску не вспомнить, что подписываешь.
 *
 * Уйти отсюда, ничего не потеряв, можно и кнопкой «назад»: картинка уже
 * лежит в папке Askya и в разделе, а подпись с альбомом — необязательные.
 * Поэтому «назад» сохраняет набранное, а не отменяет добавление.
 */
@Composable
fun ImageCardScreen(noteId: Long, onDone: () -> Unit) {
    val viewModel: ScrollViewModel = viewModel(factory = ScrollViewModel.factory(appContainer()))
    val note by remember(noteId) { viewModel.note(noteId) }
        .collectAsStateWithLifecycle(initialValue = null)
    val albums by viewModel.albums.collectAsStateWithLifecycle()

    // Подхватывается один раз, когда база ответила: перечитывать значило бы
    // затирать набранное каждым обновлением потока.
    var loaded by remember(noteId) { mutableStateOf(false) }
    var caption by remember(noteId) { mutableStateOf("") }
    var albumId by remember(noteId) { mutableStateOf<Long?>(null) }
    var creatingAlbum by remember { mutableStateOf(false) }

    LaunchedEffect(note?.id) {
        val current = note ?: return@LaunchedEffect
        if (loaded) return@LaunchedEffect
        loaded = true
        caption = current.title
        albumId = current.albumId
    }

    fun finish() {
        val current = note
        if (current == null) {
            onDone()
            return
        }
        viewModel.captionImage(current, caption, albumId, onDone)
    }

    BackHandler(onBack = ::finish)

    ScreenScaffold(
        title = "Изображение",
        onNavigationClick = ::finish,
        navigationIsBack = true,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Question("Как подпишем?")
            CaptionField(
                value = caption,
                onValueChange = { caption = it },
                onDone = ::finish,
            )

            Preview(uri = note?.uri)

            Question("В какой альбом?", top = 20.dp)
            AlbumChoice(
                albums = albums.map { it.id to it.title },
                selected = albumId,
                onSelect = { albumId = it },
                onNew = { creatingAlbum = true },
            )

            Button(
                onClick = ::finish,
                colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Accent),
                shape = RoundedCornerShape(percent = 50),
                modifier = Modifier.padding(top = 20.dp, bottom = 28.dp),
            ) {
                Text("Готово")
            }
        }
    }

    if (creatingAlbum) {
        AlbumDialog(
            title = "Новый альбом",
            initial = "",
            onDismiss = { creatingAlbum = false },
            // Альбом заводится и картинка сразу кладётся в него: его затем и
            // создавали.
            onConfirm = { name ->
                viewModel.addAlbum(name) { id -> albumId = id }
                creatingAlbum = false
            },
        )
    }
}

/** Вопрос карточки — тем же тихим голосом, каким спрашивает весь Askya. */
@Composable
private fun Question(text: String, top: androidx.compose.ui.unit.Dp = 12.dp) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(top = top, bottom = 8.dp),
    )
}

/**
 * Поле подписи. В нём уже стоит имя файла — его чаще всего и переписывают, а
 * пустое поле заставляло бы придумывать подпись каждой картинке.
 */
@Composable
private fun CaptionField(value: String, onValueChange: (String) -> Unit, onDone: () -> Unit) {
    val style = MaterialTheme.typography.titleMedium.copy(color = Ink)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(AccentSoft, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        if (value.isEmpty()) {
            Text(text = "Без подписи", style = style.copy(color = Ink.copy(alpha = 0.4f)))
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = style,
            cursorBrush = SolidColor(Accent),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Миниатюра примерно в половину экрана, формой в саму картинку.
 *
 * Размер считается от высоты экрана, а не задан в точках: половина экрана на
 * узком телефоне и на планшете — разные числа, а нужно одно и то же ощущение.
 *
 * Пока картинка читается, на её месте стоит квадрат: он занимает столько же
 * места, сколько займёт снимок, и карточка не прыгает, когда тот появится.
 */
@Composable
private fun Preview(uri: String?) {
    val bitmap = uri?.let { rememberThumbnail(it, targetPx = 1024) }
    val half = (LocalConfiguration.current.screenHeightDp * 0.5f).dp
    val ratio = bitmap?.let { it.width.toFloat() / it.height.toFloat() }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Cream),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = Modifier
            .padding(top = 20.dp)
            .cardEdge(RoundedCornerShape(20.dp))
            .then(
                when {
                    // Лежачая карточка: ширина во весь экран, высота по снимку.
                    ratio != null && ratio > 1f -> Modifier.fillMaxWidth().aspectRatio(ratio)
                    // Стоячая: высота в половину экрана, ширина по снимку.
                    ratio != null -> Modifier.height(half).aspectRatio(ratio)
                    else -> Modifier.height(half).aspectRatio(1f)
                },
            ),
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                // Fit, а не Crop: карточка уже той же формы, что и снимок, и
                // обрезать нечего — но у крайних пропорций лучше поля, чем
                // срезанный край.
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Картинка читается…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

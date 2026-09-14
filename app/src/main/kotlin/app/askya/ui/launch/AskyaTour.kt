package app.askya.ui.launch

import app.askya.resources.Res
import app.askya.resources.ic_menu_settings
import app.askya.resources.ic_wordmark
import org.jetbrains.compose.resources.painterResource
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.askya.R
import app.askya.ui.components.fadingVerticalScroll
import app.askya.ui.navigation.Destination
import app.askya.ui.theme.Accent
import app.askya.ui.theme.AccentSoft
import app.askya.ui.theme.cardEdge

/**
 * Первое знакомство с Askya.
 *
 * Показывается один раз — на первом запуске, после заставки, — и открывается
 * заново по просьбе из настроек. Всё остальное время его нет: приложение,
 * объясняющее себя на каждом входе, объясняет, что ему не доверяют.
 *
 * ## Меню по краям, разделы внутри
 *
 * Первый шаг и последний — про само приложение и про то, как в нём ходят, и
 * на них стоит меню, нарисованное как есть: тот же вордмарк, те же значки, тот
 * же нижний ряд. Человек знакомится с той самой картинкой, которую увидит,
 * когда потянет от левого края, — и узнает её потом.
 *
 * Между ними раздел **открывается**: на месте меню встаёт его собственный
 * экран и сам себя проигрывает ([SectionScene]). Подсветить строку живого меню
 * вместо этого не вышло бы — пришлось бы открыть меню за человека, не дать ему
 * его закрыть и провести по пяти разделам подряд, то есть на несколько минут
 * забрать управление. А подсвеченное слово «AskyaEcho» и без того ничего не
 * показывает: раздел узнают не по названию в списке, а по тому, что в нём
 * лежит.
 *
 * ## Взаимодействие
 *
 * Показанное — не картинка, а кнопка: по меню и по сцене раздела переходят
 * дальше.
 * Знакомство идёт от нажатия к нажатию, а не само собой по таймеру: читают
 * люди с разной скоростью, а карточка, уехавшая посреди фразы, — это не
 * рассказ, а реклама. Рядом всегда стоит «Пропустить»: тот, кому это не нужно,
 * должен выходить в одно касание, а не досматривать до конца.
 *
 * Раздел выделяется тем же, чем в Askya выделено выбранное: плашкой акцента и
 * цветом знака. Остальные не гаснут в ноль, а отступают: видно, что они есть и
 * что до них дойдёт очередь.
 */
@Composable
fun AskyaTour(onDone: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    val last = step == STEPS.lastIndex

    val next: () -> Unit = { if (last) onDone() else step++ }

    // «Назад» закрывает знакомство целиком, а не отступает на шаг: человек,
    // нажавший назад, хочет в приложение, а не в предыдущую карточку.
    BackHandler(onBack = onDone)

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Непрозрачный лист, а не полупрозрачная кисея: это отдельный
            // разговор, а не подсказка поверх работы.
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Знакомство",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "Пропустить",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onDone)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }

            // Макет занимает то, что осталось от карточки, и прокручивается,
            // если не помещается: на невысоком экране нижние разделы иначе
            // ушли бы под карточку, а знакомство с приложением не должно
            // начинаться с обрезанного меню.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fadingVerticalScroll(),
                // Посередине оставшегося, а не под самой шапкой: карточка внизу
                // и макет наверху иначе стоят по краям, а между ними — пустой
                // лист в треть экрана.
                contentAlignment = Alignment.Center,
            ) {
                // Смена шага — это «раздел открылся»: новое приезжает,
                // подрастая из глубины, а не подменяется на месте.
                AnimatedContent(
                    targetState = step,
                    transitionSpec = {
                        (fadeIn(tween(260)) + scaleIn(tween(320), initialScale = 0.92f))
                            .togetherWith(
                                fadeOut(tween(150)) + scaleOut(tween(220), targetScale = 0.96f),
                            )
                    },
                    label = "scene",
                ) { number ->
                    val at = STEPS[number]
                    val place = at.destination
                    if (place == null) {
                        MenuMock(step = at, onHighlightClick = next)
                    } else {
                        SectionScene(
                            destination = place,
                            modifier = Modifier.clickable(onClick = next),
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            StepCard(index = step, onNext = next)

            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * Меню Askya, нарисованное как есть, — и одна его строка, выделенная сейчас.
 *
 * Значки берутся те же самые (`R.drawable.ic_menu_*`), что и в живом меню: это
 * не иллюстрация к приложению, а его портрет, и разойтись с ним он не должен.
 */
@Composable
private fun MenuMock(
    step: TourStep,
    onHighlightClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .cardEdge(RoundedCornerShape(28.dp))
            .padding(vertical = 14.dp),
    ) {
        // Вордмарк — то же имя пером, что и в шапке настоящего меню. На первом
        // шаге разговор идёт про само приложение, и выделено оно.
        Highlighted(on = step.mark == TourMark.NAME, onClick = onHighlightClick) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_wordmark),
                    contentDescription = "Askya",
                    tint = if (step.mark == TourMark.NAME) {
                        Accent
                    } else {
                        MaterialTheme.colorScheme.onBackground
                    },
                )
            }
        }

        Destination.entries.forEach { destination ->
            val on = step.mark == TourMark.SECTION && step.destination == destination
            Highlighted(on = on, onClick = onHighlightClick) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(destination.icon),
                        contentDescription = null,
                        tint = if (on) Accent else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp),
                    )
                    Text(
                        text = destination.label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (on) Accent else MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 16.dp),
                    )
                }
            }
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
        )

        // Нижний ряд меню: то, что делают, не уходя из него.
        Highlighted(on = step.mark == TourMark.BOTTOM, onClick = onHighlightClick) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                val tint = if (step.mark == TourMark.BOTTOM) {
                    Accent
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
                MockButton(label = "Заметка", tint = tint) {
                    Icon(
                        imageVector = Icons.Outlined.EditNote,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(22.dp),
                    )
                }
                MockButton(label = "play Echo", tint = tint) {
                    Icon(
                        imageVector = Icons.Outlined.PlayArrow,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(22.dp),
                    )
                }
                MockButton(label = "Настройки", tint = tint) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_menu_settings),
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

/**
 * Выделенное место меню.
 *
 * Плашка акцента, чуть выросшая строка и полный цвет — против отступивших
 * соседей. Рост считается пружиной, а не мгновенным скачком: выделение должно
 * приходить движением, за которым глаз успевает.
 *
 * Нажимается вся строка целиком, а не слово в ней: по подсвеченному и идут
 * дальше, и промахнуться мимо главного действия экрана нельзя.
 */
@Composable
private fun Highlighted(
    on: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val grow by animateFloatAsState(
        targetValue = if (on) 1f else 0.98f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 320f),
        label = "grow",
    )
    val fade by animateFloatAsState(
        targetValue = if (on) 1f else 0.42f,
        animationSpec = tween(260),
        label = "fade",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .graphicsLayer {
                scaleX = grow
                scaleY = grow
            }
            .alpha(fade)
            .clip(RoundedCornerShape(16.dp))
            .background(if (on) AccentSoft else Color.Transparent)
            .then(if (on) Modifier.clickable(onClick = onClick) else Modifier),
        content = { content() },
    )
}

/** Кнопка нижнего ряда в макете: знак и подпись под ним. */
@Composable
private fun MockButton(label: String, tint: Color, icon: @Composable () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(vertical = 6.dp),
    ) {
        icon()
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/**
 * Карточка шага: что это и что в нём есть.
 *
 * Та же карточка, что и всё раскрытое в Askya, — 28 скруглений и своя обводка
 * в темноте. Меняется она въездом сбоку: карточки сменяют друг друга, и
 * движение говорит, что рассказ идёт вперёд, а не что экран перерисовался.
 *
 * Внутри — название и строчка под ним, и больше ничего: знакомство читают
 * глазами по диагонали, и список возможностей на первом запуске не читает
 * никто. Что раздел умеет, человек увидит в самом разделе.
 */
@Composable
private fun StepCard(index: Int, onNext: () -> Unit) {
    // Карточка рисуется по номеру, отданному анимации, а не по тому, который
    // снаружи: иначе уезжающая половина успевала бы перерисоваться новым
    // текстом, и вместо смены карточек получалась бы одна и та же, дважды
    // проехавшая по экрану.
    AnimatedContent(
        targetState = index,
        transitionSpec = {
            (slideInHorizontally { width -> width / 3 } + fadeIn(tween(220)))
                .togetherWith(slideOutHorizontally { width -> -width / 3 } + fadeOut(tween(160)))
        },
        label = "step",
    ) { number ->
        val step = STEPS[number]
        val last = number == STEPS.lastIndex
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surface)
                .cardEdge(RoundedCornerShape(28.dp))
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            Text(
                text = step.title,
                fontFamily = FontFamily.Serif,
                fontSize = 24.sp,
                lineHeight = 30.sp,
                letterSpacing = (-0.3).sp,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = step.about,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Dots(index = number, count = STEPS.size, modifier = Modifier.weight(1f))
                Text(
                    text = if (last) "Начать" else "Дальше",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(Accent)
                        .clickable(onClick = onNext)
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                )
            }
        }
    }
}

/** Точки внизу: сколько всего карточек и где мы в них. */
@Composable
private fun Dots(index: Int, count: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { number ->
            val here = number == index
            Box(
                modifier = Modifier
                    .height(6.dp)
                    // Пройденное и будущее — точки, нынешнее — черта: место в
                    // рассказе видно и без счёта.
                    .width(if (here) 18.dp else 6.dp)
                    .clip(CircleShape)
                    .background(
                        if (here) Accent else MaterialTheme.colorScheme.outline,
                    ),
            )
        }
    }
}

/** Что подсвечено на этом шаге: имя приложения, раздел или нижний ряд меню. */
private enum class TourMark { NAME, SECTION, BOTTOM }

/** Один шаг знакомства: что выделено и что об этом сказано. */
private data class TourStep(
    val mark: TourMark,
    val destination: Destination? = null,
    val title: String,
    val about: String,
)

/**
 * Сам рассказ.
 *
 * Порядок — тот же, что в меню, и это не случайность: человек, прошедший
 * знакомство, второй раз видит те же строчки в том же порядке уже в живом
 * меню. Первая карточка про приложение целиком, последняя — про то, как в это
 * меню попасть; между ними по одной на раздел.
 *
 * В каждой — одна строчка о том, **что** это за раздел. Не список кнопок:
 * знакомство должно занимать минуту, а не заменять собой приложение.
 */
private val STEPS = listOf(
    TourStep(
        mark = TourMark.NAME,
        title = "Это Askya",
        about = "Личный блокнот: день, записи, музыка, видео, деньги.",
    ),
    TourStep(
        mark = TourMark.SECTION,
        destination = Destination.TODAY,
        title = "AskyaDay — твой день",
        about = "С этого раздела открывается приложение: сегодняшний день, разложенный " +
            "карточками с делами.",
    ),
    TourStep(
        mark = TourMark.SECTION,
        destination = Destination.NOTES,
        title = "Scroll — все записи",
        about = "Заметки, изображения, списки, голосовые заметки.",
    ),
    TourStep(
        mark = TourMark.SECTION,
        destination = Destination.ECHO,
        title = "AskyaEcho — музыка",
        about = "Твоя музыка и аудиозаписи на устройстве плюс лаборатория для редактирования.",
    ),
    TourStep(
        mark = TourMark.SECTION,
        destination = Destination.VIDEO,
        title = "AskyaV — видео",
        about = "Видеоплеер с функциями VLC и та же лаборатория.",
    ),
    TourStep(
        mark = TourMark.SECTION,
        destination = Destination.LEDGER,
        title = "Ledger — деньги",
        about = "Расход, доход, счета, статистика.",
    ),
    TourStep(
        mark = TourMark.BOTTOM,
        title = "Меню и настройки",
        about = "Быстрые действия и раздел настроек.",
    ),
)

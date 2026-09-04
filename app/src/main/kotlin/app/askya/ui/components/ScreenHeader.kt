package app.askya.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.askya.R
import app.askya.ui.theme.FlowerInk

/**
 * Шапка экрана: слева кнопка меню (или «назад» на детальном экране), затем
 * заголовок засечным шрифтом — той же гарнитурой, что в вордмарке на иконке.
 *
 * ## Меню открывается цветком, а не полосками
 *
 * Три полоски — знак ничей: он одинаков в банке, в магазине и в почте, и по
 * нему не узнать, чьё это приложение. Цветок Askya узнаётся: это тот же знак,
 * что на иконке запуска и на заставке, и меню открывается лицом приложения, а
 * не значком из чужого набора. Прежде так было в одном AskyaEcho, а с тех пор
 * стало общим правилом — отсюда он и стоит здесь по умолчанию.
 *
 * Знак рисуется своими цветами, а не тонируется под текст: он не значок
 * действия, а лицо приложения, и серым оно было бы чужим. По той же причине
 * «назад» остаётся стрелкой — это как раз действие, и лица у него нет.
 *
 * Раздел может поставить на место цветка собственный знак ([navigationIcon]).
 */
@Composable
fun ScreenHeader(
    title: String,
    onNavigationClick: () -> Unit,
    modifier: Modifier = Modifier,
    navigationIsBack: Boolean = false,
    @DrawableRes navigationIcon: Int? = null,
    navigationLabel: String = "Меню",
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (navigationIsBack) {
            HeaderIcon(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Назад",
                onClick = onNavigationClick,
            )
        } else if (navigationIcon == null) {
            // Цветок — знак приложения, и красится он выбранной краской, а не
            // той, что зашита в вектор: см. [AskyaFlower].
            HeaderIcon(
                painter = painterResource(R.drawable.ic_flower),
                contentDescription = navigationLabel,
                onClick = onNavigationClick,
                tint = FlowerInk,
            )
        } else {
            HeaderIcon(
                painter = painterResource(navigationIcon),
                contentDescription = navigationLabel,
                onClick = onNavigationClick,
            )
        }

        Text(
            text = title,
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp),
            fontFamily = FontFamily.Serif,
            fontSize = 26.sp,
            letterSpacing = (-0.3).sp,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), content = actions)
    }
}

/** Круглая кнопка в шапке — используется и для навигации, и для действий справа. */
@Composable
fun HeaderIcon(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onBackground,
        )
    }
}

/** Тот же круг, но со знаком раздела: он идёт картинкой и своим цветом. */
@Composable
fun HeaderIcon(
    painter: Painter,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color? = null,
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painter,
            contentDescription = contentDescription,
            colorFilter = tint?.let { ColorFilter.tint(it) },
            modifier = Modifier.size(26.dp),
        )
    }
}

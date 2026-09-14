package app.askya.ui.components

import org.jetbrains.compose.resources.DrawableResource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Общий каркас экрана: шапка, содержимое и необязательная кнопка в правом
 * нижнем углу. Material3 Scaffold не берётся намеренно — он тянет собственные
 * фон и отступы, из-за которых кремовая палитра и своя шапка расходятся.
 */
@Composable
fun ScreenScaffold(
    title: String,
    onNavigationClick: () -> Unit,
    modifier: Modifier = Modifier,
    navigationIsBack: Boolean = false,
    navigationIcon: DrawableResource? = null,
    navigationLabel: String = "Меню",
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(
                title = title,
                onNavigationClick = onNavigationClick,
                navigationIsBack = navigationIsBack,
                navigationIcon = navigationIcon,
                navigationLabel = navigationLabel,
                actions = actions,
            )
            content()
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
            content = { floatingActionButton() },
        )
    }
}

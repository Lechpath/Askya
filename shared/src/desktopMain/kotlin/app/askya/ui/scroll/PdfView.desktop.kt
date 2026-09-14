package app.askya.ui.scroll

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.askya.platform.LocalPlatformContext
import app.askya.platform.toast

/**
 * PDF у Windows-версии открывает программа, которую Windows назначила для
 * PDF, — браузер или читалка. Своей рисовалки PDF у компьютера нет, а
 * библиотека ради неё весила бы больше, чем весь Scroll.
 */
@Composable
actual fun PdfView(uri: String) {
    val context = LocalPlatformContext.current
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "PDF открывает программа Windows — та же, что открывает его из проводника.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        TextButton(
            onClick = {
                if (!openFile(context, uri, "application/pdf")) {
                    context.toast("Файла нет на месте — его могли убрать или переложить")
                }
            },
            modifier = Modifier.padding(top = 12.dp),
        ) {
            Text("Открыть PDF")
        }
    }
}

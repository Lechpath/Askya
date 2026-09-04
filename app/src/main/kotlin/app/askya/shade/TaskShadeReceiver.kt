package app.askya.shade

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.askya.app.AskyaApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Нажатие в шторке: отметить строку списка или отметить всё дело.
 *
 * Приёмник, а не служба и не запуск приложения: отметка — это одна строка в
 * базе, и поднимать ради неё экран значило бы отобрать у шторки то
 * единственное, ради чего она заведена, — отметить, не открывая Askya.
 *
 * [goAsync] нужен потому, что база отвечает не мгновенно: без него система
 * посчитала бы приёмник отработавшим сразу после `onReceive` и была бы вправе
 * убить процесс посреди записи. Отпускается он в любом случае — и когда всё
 * получилось, и когда база отказала: неотпущенный держит процесс живым, пока
 * система не отберёт его сама.
 */
class TaskShadeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_ID, 0L)
        if (id <= 0L) return

        val app = context.applicationContext
        val container = (app as AskyaApplication).container
        val done = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_TOGGLE -> container.deedTaskRepository.toggle(id)
                    // Дело отмечено целиком — строки не трогаются: человек
                    // сказал «с этим закончено», а не «всё в нём сделано», и
                    // дописывать за него галочки Askya не станет. Из шторки
                    // дело уйдёт само: она показывает неотмеченные.
                    ACTION_DONE -> container.scheduleRepository.setDone(id, true)
                    else -> Unit
                }
                TaskShade.refresh(app)
            } finally {
                done.finish()
            }
        }
    }

    companion object {
        const val ACTION_TOGGLE = "app.askya.shade.TOGGLE"
        const val ACTION_DONE = "app.askya.shade.DONE"

        /** Номер строки у [ACTION_TOGGLE] и номер дела у [ACTION_DONE]. */
        const val EXTRA_ID = "askya.shade.id"
    }
}

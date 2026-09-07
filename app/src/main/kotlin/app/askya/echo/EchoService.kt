package app.askya.echo

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import app.askya.R
import app.askya.app.AskyaApplication
import app.askya.app.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * AskyaEcho в шторке и в системном плеере.
 *
 * Музыка, которую нельзя остановить, не открыв приложение, — не музыка, а
 * ловушка: она играет из кармана, а кнопка от неё осталась на экране, до
 * которого ещё идти. Поэтому у играющего Echo есть два лица за пределами
 * приложения — уведомление в шторке и карточка в центре управления (шторка
 * быстрых настроек, экран блокировки, часы, машина).
 *
 * Обе даёт одна и та же вещь — `MediaSession`: система читает из неё, что
 * играет и что с этим можно делать, и рисует свои кнопки сама. Уведомление
 * лишь ссылается на сессию ([Notification.MediaStyle]) — оттуда и обложка на
 * весь экран блокировки, и полоса перемотки.
 *
 * Платформенный `MediaSession`, а не `MediaSessionCompat` из androidx.media:
 * minSdk здесь 26, и всё, ради чего берут совместимую обёртку, есть в системе
 * начиная с Android 5. Тянуть библиотеку ради того же самого — против того же
 * правила, по которому в плеере стоит `MediaPlayer`, а не ExoPlayer.
 *
 * Служба переднего плана, потому что звук идёт, когда приложение свёрнуто:
 * без неё система вправе убить процесс посреди песни. Пока играет — уведомление
 * несъёмное (таково правило переднего плана); на паузе оно отпускается
 * ([ServiceCompat.STOP_FOREGROUND_DETACH]) и его можно смахнуть — смахнули,
 * значит дослушали, и плеер останавливается совсем.
 *
 * Своего состояния служба не держит: она смотрит на тот же поток [EchoPlayer],
 * что и экран. Плеер живёт в контейнере приложения и переживает и службу, и
 * экран, — а служба только показывает его наружу.
 */
class EchoService : Service() {

    private val player: EchoPlayer by lazy {
        (applicationContext as AskyaApplication).container.echoPlayer
    }

    private lateinit var session: MediaSession

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Обложка грузится один раз на дорожку: файл читают, а не держат в руке. */
    private var artUri: String? = null
    private var art: Bitmap? = null
    private var loading: Job? = null

    /** Стоит ли служба на переднем плане прямо сейчас. */
    private var foreground = false

    /**
     * Вставала ли она туда хоть раз после запуска.
     *
     * Система даёт на это пять секунд после `startForegroundService` и убивает
     * службу, которая промолчала, — даже если музыку за эти секунды успели
     * поставить на паузу. Поэтому первый показ всегда идёт передним планом, а
     * отпускается уведомление уже следом.
     */
    private var promised = false

    override fun onCreate() {
        super.onCreate()
        ensureChannel()

        session = MediaSession(this, "AskyaEcho").apply {
            setCallback(
                object : MediaSession.Callback() {
                    // Кнопки системы говорят, что сделать, а не что сейчас:
                    // «play» на играющем плеере — не переключатель, а команда.
                    override fun onPlay() {
                        if (!player.state.value.playing) player.toggle()
                    }

                    override fun onPause() = player.pause()

                    override fun onSkipToNext() = player.next()

                    override fun onSkipToPrevious() = player.previous()

                    override fun onSeekTo(pos: Long) = player.seekTo(pos)

                    override fun onStop() = stopEverything()
                },
            )
            isActive = true
        }

        // Один подписчик на весь век службы: и уведомление, и сессия
        // пересобираются из того же состояния, что показывает экран.
        scope.launch {
            player.state.collect { state -> publish(state) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> if (!player.state.value.playing) player.toggle()
            ACTION_PAUSE -> player.pause()
            ACTION_NEXT -> player.next()
            ACTION_PREVIOUS -> player.previous()
            ACTION_STOP -> stopEverything()
        }
        // NOT_STICKY: убитую систему службу незачем воскрешать без музыки —
        // она поднялась бы с пустым уведомлением и ничего не играла.
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        session.isActive = false
        session.release()
        super.onDestroy()
    }

    /**
     * Дорожки не осталось — служба не нужна.
     *
     * Останавливается только плеер: его пустое состояние прилетит сюда потоком,
     * и служба свернётся сама ([publish]). Звать `stopSelf` отсюда напрямую
     * значило бы описать один и тот же уход в двух местах.
     */
    private fun stopEverything() = player.stop()

    private fun stop() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        foreground = false
        runCatching { NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID) }
        stopSelf()
    }

    /** Отдать наружу то, что сейчас с плеером: сессии — данные, шторке — вид. */
    private fun publish(state: EchoState) {
        val track = state.track
        if (track == null) {
            stop()
            return
        }

        artwork(track)

        session.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, track.title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, track.artist)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, track.album)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, state.durationMs)
                // Обложка в метаданных — то, чем система заливает карточку в
                // центре управления и экран блокировки.
                .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, art)
                .build(),
        )

        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or
                        PlaybackState.ACTION_PAUSE or
                        PlaybackState.ACTION_PLAY_PAUSE or
                        PlaybackState.ACTION_SKIP_TO_NEXT or
                        PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackState.ACTION_SEEK_TO or
                        PlaybackState.ACTION_STOP,
                )
                .setState(
                    if (state.playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                    // Позиция спрашивается у самого плеера: в потоке она
                    // обновляется по событиям, и полоса в шторке прыгала бы
                    // назад на каждой смене очереди.
                    player.position(),
                    if (state.playing) 1f else 0f,
                )
                .build(),
        )

        show(state)
    }

    /**
     * Уведомление плеера.
     *
     * Пока играет — служба переднего плана: система обязуется не убивать
     * процесс, а уведомление держится в шторке. На паузе оно отпускается и
     * становится съёмным: пауза — это уже не «идёт музыка», и висеть намертво
     * ей не за что.
     */
    private fun show(state: EchoState) {
        val notification = build(state)

        if (state.playing || !promised) {
            // Под `runCatching`, потому что бросает: с Android 12 система
            // отказывает службе переднего плана, поднятой из фона
            // (`ForegroundServiceStartNotAllowedException`). Отказ этот —
            // не повод потерять подписку на плеер: вылети исключение отсюда,
            // и сборщик состояния умер бы вместе с ней, а уведомление
            // перестало бы обновляться до конца жизни службы — то есть шторка
            // замерла бы на той песне, при которой это случилось.
            val raised = runCatching {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    } else {
                        0
                    },
                )
            }.isSuccess

            if (raised) {
                foreground = true
                promised = true
            } else {
                // Передним планом не вышло — уведомление всё равно должно
                // висеть: карточку плеера система рисует из него, и без него
                // музыку из кармана нечем остановить.
                runCatching {
                    NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
                }
            }
        }

        if (!state.playing) {
            if (foreground) {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
                foreground = false
            }
            // Разрешение на уведомления могли не дать: система тогда молча
            // откажет, и падать из-за этого музыке незачем.
            runCatching {
                NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
            }
        }
    }

    private fun build(state: EchoState): Notification {
        val track = state.track

        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_flower)
            .setContentTitle(track?.title.orEmpty())
            .setContentText(track?.artist.orEmpty())
            .setSubText("AskyaEcho")
            .setLargeIcon(art)
            .setContentIntent(open)
            // Смахнули на паузе — значит, дослушали: плеер останавливается,
            // а не остаётся висеть невидимкой.
            .setDeleteIntent(command(ACTION_STOP))
            .setOngoing(state.playing)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setShowWhen(false)
            // Значки системные: рисовать своё «предыдущее» и «следующее»
            // незачем — в шторке они должны выглядеть так же, как у всех.
            .addAction(
                Notification.Action.Builder(
                    android.graphics.drawable.Icon.createWithResource(
                        this,
                        android.R.drawable.ic_media_previous,
                    ),
                    "Прошлая",
                    command(ACTION_PREVIOUS),
                ).build(),
            )
            .addAction(
                Notification.Action.Builder(
                    android.graphics.drawable.Icon.createWithResource(
                        this,
                        if (state.playing) {
                            android.R.drawable.ic_media_pause
                        } else {
                            android.R.drawable.ic_media_play
                        },
                    ),
                    if (state.playing) "Пауза" else "Играть",
                    command(if (state.playing) ACTION_PAUSE else ACTION_PLAY),
                ).build(),
            )
            .addAction(
                Notification.Action.Builder(
                    android.graphics.drawable.Icon.createWithResource(
                        this,
                        android.R.drawable.ic_media_next,
                    ),
                    "Следующая",
                    command(ACTION_NEXT),
                ).build(),
            )
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(session.sessionToken)
                    // Три кнопки и в свёрнутом виде: свёрнутым уведомление и
                    // видят чаще всего, а «дальше» нажимают едва ли не больше,
                    // чем паузу.
                    .setShowActionsInCompactView(0, 1, 2),
            )
            .build()
    }

    /**
     * Обложка новой дорожки.
     *
     * Читается в фоне и только при смене файла: у песни она весит с фотографию,
     * а меняется раз в три минуты. Пришла — состояние публикуется заново, уже
     * с картинкой.
     */
    private fun artwork(track: Track) {
        if (track.uri == artUri) return
        artUri = track.uri
        art = null
        loading?.cancel()
        loading = scope.launch {
            val loaded = loadArtwork(applicationContext, track.albumId, track.uri)
            // Дорожка могла смениться, пока читали: чужая обложка хуже, чем
            // никакой.
            if (artUri == track.uri) {
                art = loaded
                player.state.value.takeIf { it.track != null }?.let { publish(it) }
            }
        }
    }

    private fun command(action: String): PendingIntent = PendingIntent.getService(
        this,
        action.hashCode(),
        Intent(this, EchoService::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /**
     * Канал плеера — тихий (`LOW`): музыка сама себя объявляет, и звенеть
     * уведомлением о том, что заиграла песня, было бы дико.
     */
    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "AskyaEcho", NotificationManager.IMPORTANCE_LOW).apply {
                setSound(null, null)
                setShowBadge(false)
            },
        )
    }

    companion object {
        /**
         * Канал плеера. Не приватный: настройки Echo спрашивают у системы,
         * не выключен ли он, — и два одинаковых имени в двух местах разошлись
         * бы в первый же раз, когда правят одно из них.
         */
        internal const val CHANNEL = "echo"
        private const val NOTIFICATION_ID = 1_000

        private const val ACTION_PLAY = "app.askya.echo.PLAY"
        private const val ACTION_PAUSE = "app.askya.echo.PAUSE"
        private const val ACTION_NEXT = "app.askya.echo.NEXT"
        private const val ACTION_PREVIOUS = "app.askya.echo.PREVIOUS"
        private const val ACTION_STOP = "app.askya.echo.STOP"

        /**
         * Поднять службу. Зовётся плеером в момент, когда дорожка пошла, —
         * то есть всегда из приложения на переднем плане: с Android 12
         * запускать службу переднего плана из фона нельзя, и делать это
         * «на всякий случай» значило бы ловить отказ системы.
         */
        fun start(context: Context) {
            val intent = Intent(context, EchoService::class.java)
            runCatching { context.startForegroundService(intent) }
        }

    }
}

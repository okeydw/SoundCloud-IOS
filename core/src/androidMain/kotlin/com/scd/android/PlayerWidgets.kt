package com.scd.android

import com.scd.android.core.R

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import java.io.File
import java.util.concurrent.Executors

object PlayerWidgets {
    const val ACTION_TOGGLE = "com.scd.android.widget.TOGGLE"
    const val ACTION_NEXT = "com.scd.android.widget.NEXT"
    const val ACTION_PREV = "com.scd.android.widget.PREV"

    private const val PREFS = "player_widget"
    private const val ART_FILE = "widget_art.png"

    data class State(
        val title: String = "",
        val artist: String = "",
        val playing: Boolean = false,
        val art: String? = null,
    ) {
        val hasTrack: Boolean get() = title.isNotBlank()
    }

    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "widget").apply { isDaemon = true } }
    private val main = Handler(Looper.getMainLooper())
    private val loader by lazy { ScBitmapLoader(Api.http) }

    @Volatile private var last: State? = null
    @Volatile private var art: Bitmap? = null
    @Volatile private var artKey: String? = null

    private val providers = listOf(PlayerWidgetSmall::class.java, PlayerWidgetLarge::class.java)

    private fun ids(context: Context): Map<Class<*>, IntArray> {
        val mgr = AppWidgetManager.getInstance(context)
        return providers.associateWith { mgr.getAppWidgetIds(ComponentName(context, it)) }.filterValues { it.isNotEmpty() }
    }

    fun publish(context: Context, player: Player) {
        val app = context.applicationContext
        val md = player.currentMediaItem?.mediaMetadata
        val playing = player.isPlaying || (player.playWhenReady && player.playbackState == Player.STATE_BUFFERING)
        val state = State(
            title = md?.title?.toString().orEmpty(),
            artist = md?.artist?.toString().orEmpty(),
            playing = playing,
            art = md?.artworkUri?.toString(),
        )
        if (state == last) return
        last = state
        worker.execute { save(app, state) }
        if (ids(app).isEmpty()) return
        render(app, state)
        if (state.art != artKey) loadArt(app, state)
    }

    fun refresh(context: Context) {
        val app = context.applicationContext
        worker.execute {
            val state = last ?: restore(app).also { last = it }
            if (art == null && state.art != null) {
                art = runCatching { BitmapFactory.decodeFile(File(app.cacheDir, ART_FILE).path) }.getOrNull()
                if (art != null) artKey = state.art
            }
            main.post {
                render(app, state)
                if (state.art != null && state.art != artKey) loadArt(app, state)
            }
        }
    }

    private fun loadArt(context: Context, state: State) {
        val uri = state.art
        artKey = uri
        if (uri == null) {
            art = null
            render(context, state)
            return
        }
        worker.execute {
            val bmp = runCatching { loader.loadBitmap(android.net.Uri.parse(uri)).get() }.getOrNull()?.let { rounded(it) }
            if (artKey != uri) return@execute
            art = bmp
            if (bmp != null) runCatching { File(context.cacheDir, ART_FILE).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }
            main.post { last?.let { render(context, it) } }
        }
    }

    private fun rounded(src: Bitmap): Bitmap {
        val side = minOf(src.width, src.height).coerceAtLeast(1)
        val target = minOf(side, 320)
        val out = Bitmap.createBitmap(target, target, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val scale = target.toFloat() / side
        val dx = (src.width - side) / 2f
        val dy = (src.height - side) / 2f
        val shader = BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            setLocalMatrix(android.graphics.Matrix().apply {
                postTranslate(-dx, -dy)
                postScale(scale, scale)
            })
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader }
        val r = target * 0.14f
        canvas.drawRoundRect(RectF(0f, 0f, target.toFloat(), target.toFloat()), r, r, paint)
        return out
    }

    private fun render(context: Context, state: State) {
        val mgr = AppWidgetManager.getInstance(context)
        for ((cls, ids) in ids(context)) {
            val layout = if (cls == PlayerWidgetLarge::class.java) R.layout.widget_large else R.layout.widget_small
            mgr.updateAppWidget(ids, views(context, layout, cls, state))
        }
    }

    private fun views(context: Context, layout: Int, cls: Class<*>, state: State): RemoteViews {
        val v = RemoteViews(context.packageName, layout)
        v.setTextViewText(R.id.widget_title, if (state.hasTrack) state.title else context.getString(R.string.widget_idle))
        v.setTextViewText(R.id.widget_artist, if (state.hasTrack) state.artist else context.getString(R.string.widget_open))
        val bmp = art
        if (state.hasTrack && bmp != null && !bmp.isRecycled) v.setImageViewBitmap(R.id.widget_art, bmp)
        else v.setImageViewResource(R.id.widget_art, R.drawable.widget_art_placeholder)

        val accent = Prefs.accent
        v.setInt(R.id.widget_play_bg, "setColorFilter", accent)
        v.setInt(R.id.widget_play_icon, "setColorFilter", if (light(accent)) Color.BLACK else Color.WHITE)
        v.setImageViewResource(R.id.widget_play_icon, if (state.playing) R.drawable.ic_pause else R.drawable.ic_play)
        v.setContentDescription(R.id.widget_play, context.getString(if (state.playing) R.string.cd_pause else R.string.cd_play))

        val open = openApp(context)
        v.setOnClickPendingIntent(R.id.widget_root, open)
        v.setOnClickPendingIntent(R.id.widget_art, open)
        v.setOnClickPendingIntent(R.id.widget_play, if (state.hasTrack) command(context, cls, ACTION_TOGGLE) else open)
        if (layout == R.layout.widget_large) {
            v.setOnClickPendingIntent(R.id.widget_next, command(context, cls, ACTION_NEXT))
            v.setOnClickPendingIntent(R.id.widget_prev, command(context, cls, ACTION_PREV))
            v.setContentDescription(R.id.widget_next, context.getString(R.string.cd_next))
            v.setContentDescription(R.id.widget_prev, context.getString(R.string.cd_prev))
        }
        return v
    }

    private fun light(color: Int): Boolean =
        (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255.0 > 0.62

    private fun openApp(context: Context): PendingIntent {
        val intent = (context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent())
            .putExtra("open_player", true)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(context, 9100, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun command(context: Context, cls: Class<*>, action: String): PendingIntent {
        val intent = Intent(context, cls).setAction(action)
        val code = when (action) {
            ACTION_TOGGLE -> 1
            ACTION_NEXT -> 2
            else -> 3
        } + if (cls == PlayerWidgetLarge::class.java) 10 else 0
        return PendingIntent.getBroadcast(context, 9100 + code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun handle(context: Context, action: String, done: () -> Unit) {
        val app = context.applicationContext
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        future.addListener({
            val controller = runCatching { future.get() }.getOrNull()
            if (controller != null) {
                runCatching {
                    when (action) {
                        ACTION_TOGGLE -> if (controller.isPlaying) controller.pause() else if (controller.mediaItemCount > 0) {
                            controller.prepare()
                            controller.play()
                        }
                        ACTION_NEXT -> controller.seekToNext()
                        ACTION_PREV -> controller.seekToPrevious()
                    }
                }.onFailure { Logs.add("widget", "command failed: ${it.javaClass.simpleName}") }
                main.postDelayed({ controller.release() }, 1_500L)
            }
            done()
        }, ContextCompat.getMainExecutor(app))
    }

    private fun save(context: Context, s: State) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("title", s.title)
            .putString("artist", s.artist)
            .putString("art", s.art)
            .apply()
    }

    private fun restore(context: Context): State {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return State(p.getString("title", "").orEmpty(), p.getString("artist", "").orEmpty(), false, p.getString("art", null))
    }
}

abstract class PlayerWidgetBase : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        PlayerWidgets.refresh(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (val action = intent.action) {
            PlayerWidgets.ACTION_TOGGLE, PlayerWidgets.ACTION_NEXT, PlayerWidgets.ACTION_PREV -> {
                val pending = goAsync()
                PlayerWidgets.handle(context, action) { pending.finish() }
            }
            else -> super.onReceive(context, intent)
        }
    }
}

class PlayerWidgetSmall : PlayerWidgetBase()

class PlayerWidgetLarge : PlayerWidgetBase()

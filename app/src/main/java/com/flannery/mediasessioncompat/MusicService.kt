package com.flannery.mediasessioncompat

import android.app.*
import android.content.*
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.media.session.MediaButtonReceiver
import kotlin.random.Random

class MusicService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 0x123
        private const val ACTION_NEXT = "com.player.notify.next" // 下一首广播标志
        private const val ACTION_PREV = "com.player.notify.prev" // 上一首广播标志
        private const val ACTION_PLAY_PAUSE = "com.player.notify.play_state" // 播放暂停广播
        private const val ACTION_CLOSE = "com.player.notify.close" // 播放暂停广播
        private const val ACTION_LYRIC = "com.player.notify.lyric" // 播放暂停广播

        private const val DEFAULT_NOTIFICATION = "notification"
        private const val ACTION = "MUSIC_SERVICE_ACTION"
        private const val KEY_PLAY = "play"
        private const val KEY_STOP = "stop"
        private const val KEY_PROGRESS = "progress"
        const val MAX_PROGRESS = 100L

        fun startMySelf(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, MusicService::class.java))
        }

        fun play(context: Context) {
            LocalBroadcastManager.getInstance(context).sendBroadcast(Intent(ACTION).apply {
                putExtra(KEY_PLAY, "")
            })
        }

        fun stop(context: Context) {
            LocalBroadcastManager.getInstance(context).sendBroadcast(Intent(ACTION).apply {
                putExtra(KEY_STOP, "")
            })
        }

        fun setProgress(context: Context, progress: Int) {
            LocalBroadcastManager.getInstance(context).sendBroadcast(Intent(ACTION).apply {
                putExtra(KEY_PROGRESS, progress)
            })
        }

        //指定可以接收的来自锁屏页面的按键信息
        private const val MEDIA_SESSION_ACTIONS = (PlaybackStateCompat.ACTION_PLAY
                or PlaybackStateCompat.ACTION_PAUSE
                or PlaybackStateCompat.ACTION_PLAY_PAUSE
                or PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                or PlaybackStateCompat.ACTION_STOP
                or PlaybackStateCompat.ACTION_SEEK_TO)

        private fun log(text: String) {
            Log.i(MusicService::class.simpleName, "" + text)
        }

        fun getPendingFlag(): Int {
            val flag: Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            return flag
        }

        //判断是否是android 6.0
        fun isJellyBeanMR1(): Boolean {
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1
        }

        //判断是否是android 5.0
        fun isLollipop(): Boolean {
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
        }

    }

    private lateinit var mMediaSession: MediaSessionCompat
    private lateinit var mHandler: Handler
    private lateinit var mNotificationManager: NotificationManager
    private lateinit var mNotificationBuilder: NotificationCompat.Builder
    private lateinit var mNotification: Notification

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // log("broadcastReceiver intent=$intent")
            if (intent?.hasExtra(KEY_PLAY) == true) {
                // play
                isPlaying = true
                updateMetaData()
            } else if (intent?.hasExtra(KEY_STOP) == true) {
                isPlaying = false
                // stop
            } else if (intent?.hasExtra(KEY_PROGRESS) == true) {
                // progress
                progress = intent.getIntExtra(KEY_PROGRESS, 0).toLong()
                if (progress !in 0..MAX_PROGRESS) {
                    progress = Random(100).nextLong()
                }
                updatePlaybackState()
            }
        }
    }

    private var isPlaying: Boolean = false
    private var progress: Long = 40

    private val callback = object : MediaSessionCompat.Callback() {
        override fun onMediaButtonEvent(mediaButtonEvent: Intent?): Boolean {
            log("mediaButtonEvent: $mediaButtonEvent")
            return super.onMediaButtonEvent(mediaButtonEvent)
        }

        override fun onPlay() {
            super.onPlay()
            log("onPlay")
        }

        override fun onPause() {
            super.onPause()
            log("onPause")
        }

        override fun onSkipToNext() {
            super.onSkipToNext()
            log("onSkipToNext")
        }

        override fun onStop() {
            super.onStop()
            log("onStop")
        }

        override fun onSeekTo(pos: Long) {
            super.onSeekTo(pos)
            log("onSeekTo")
        }
    }

    override fun onCreate() {
        super.onCreate()
        log("onCreate")
        mNotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mHandler = Handler(Looper.getMainLooper())
        mMediaSession = MediaSessionCompat(this, "MusicService")
        // 指明支持的按键信息类型
        mMediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_QUEUE_COMMANDS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)

        mMediaSession.setCallback(callback, mHandler)
        mMediaSession.isActive = true

        initNotify()
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(broadcastReceiver, IntentFilter().apply {
                addAction(ACTION)
            })
    }

    fun updateNotification(isChange: Boolean) {
        if (isChange) {
            mNotificationBuilder.setContentTitle("title")
            mNotificationBuilder.setContentText("artistName")
            mNotificationBuilder.setTicker("title-artistName")
        }
        if (isPlaying) {
            mNotificationBuilder.mActions[1] = NotificationCompat.Action(R.drawable.ic_pause,
                "",
                retrievePlaybackAction(ACTION_PLAY_PAUSE))
        } else {
            mNotificationBuilder.mActions[1] = NotificationCompat.Action(R.drawable.ic_play,
                "",
                retrievePlaybackAction(ACTION_PLAY_PAUSE))
        }
        mNotification = mNotificationBuilder.build()
        startForeground(NOTIFICATION_ID, mNotification)
        mNotificationManager.notify(NOTIFICATION_ID, mNotification)
    }

    private fun cancelNotification() {
        stopForeground(true)
        mNotificationManager.cancel(NOTIFICATION_ID)
    }

    fun updatePlaybackState() {
        log("updatePlaybackState progress=$progress")
        val state =
            if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        mMediaSession.setPlaybackState(
            PlaybackStateCompat.Builder().setActions(MEDIA_SESSION_ACTIONS)
                .setState(state, progress, 1F).build()
        )
    }

    fun updateMetaData() {
        val metaData = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, "title")
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "artist")
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "album")
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST, "artist")
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, MAX_PROGRESS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            metaData.putLong(MediaMetadataCompat.METADATA_KEY_NUM_TRACKS, 1)
        }
        mMediaSession.setMetadata(metaData.build())
    }

    fun getMediaSession(): MediaSessionCompat.Token {
        return mMediaSession.sessionToken
    }

    fun getCurrentPosition(): Long {
        return progress
    }

    fun release() {
        mMediaSession.setCallback(null)
        mMediaSession.isActive = false
        mMediaSession.release()
    }

    fun initNotify() {
        val playButtonResId = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        val title = if (isPlaying) "pause" else "play"
        val nowPlayingIntent = Intent(this, MainActivity::class.java)
        nowPlayingIntent.action = DEFAULT_NOTIFICATION
        val clickIntent = PendingIntent.getActivity(this, 0, nowPlayingIntent, getPendingFlag())
        mNotificationBuilder = NotificationCompat.Builder(this, initChannelId())
            .setSmallIcon(R.drawable.ic_music)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(clickIntent)
            .setContentTitle("contentTitle")
            .setWhen(System.currentTimeMillis())
            .addAction(R.drawable.ic_skip_previous, "pre", retrievePlaybackAction(ACTION_PREV))
            .addAction(playButtonResId, "title", retrievePlaybackAction(ACTION_PLAY_PAUSE))
            .addAction(R.drawable.ic_skip_next, "next", retrievePlaybackAction(ACTION_NEXT))
            .addAction(R.drawable.ic_lyric, "lyric", retrievePlaybackAction(ACTION_LYRIC))
            .addAction(R.drawable.ic_clear, "close", retrievePlaybackAction(ACTION_CLOSE))
            .setDeleteIntent(MediaButtonReceiver.buildMediaButtonPendingIntent(this,
                PlaybackStateCompat.ACTION_STOP))
        if (isJellyBeanMR1()) {
            mNotificationBuilder.setShowWhen(false)
        }
        if (isLollipop()) {
            mNotificationBuilder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            val style = androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(getMediaSession())
                .setShowActionsInCompactView(1, 0)
            mNotificationBuilder.setStyle(style)
        }
        mNotification = mNotificationBuilder.build()
    }

    fun retrievePlaybackAction(action: String): PendingIntent? {
        val intent = Intent(action)
        intent.component = ComponentName(this, MusicService::class.java)
        return PendingIntent.getService(this, 0, intent, getPendingFlag())
    }

    fun initChannelId(): String {
        // 通知渠道的id
        val id = "CHANNEL_ID"
        // 用户可以看到的通知渠道的名字
        val name = "项目名称"
        val description = "Notification Bar Playback Control"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_LOW
            val mChannel = NotificationChannel(id, name, importance)
            mChannel.description = description
            mChannel.enableLights(false)
            mChannel.enableVibration(false)
            // 最后再notificationmanager众创建该通知渠道
            mNotificationManager.createNotificationChannel(mChannel)
        }
        return id
    }

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        //return super.onStartCommand(intent, flags, startId)
        updateNotification(false)
        return START_NOT_STICKY
    }
}
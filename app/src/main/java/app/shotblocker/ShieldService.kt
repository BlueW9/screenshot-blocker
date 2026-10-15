package app.shotblocker

import android.app.*
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import androidx.core.app.ServiceCompat

/**
 * خدمة أمامية خفيفة: تعرض طبقة شفافة بعلامة FLAG_SECURE فوق الشاشة
 * فتظهر سوداء في لقطات الشاشة وتسجيلها. تُظهر الطبقة فقط عند الحاجة.
 */
class ShieldService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var overlay: View? = null
    private var current: String? = null
    private var since = 0L

    private val loop = object : Runnable {
        override fun run() {
            try { tick() } catch (_: Exception) { }
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("shield", getString(R.string.notif_title), NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(this, "shield")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        ServiceCompat.startForeground(this, 1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        since = System.currentTimeMillis() - 6 * 3600_000L
        handler.post(loop)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Prefs.enabled(this)) stopSelf()
        return START_STICKY
    }

    private fun tick() {
        if (!Prefs.enabled(this)) { stopSelf(); return }
        if (!Settings.canDrawOverlays(this)) { hide(); return }
        val want = if (Prefs.allApps(this)) true else {
            updateForeground()
            current?.let { it in Prefs.packages(this) } ?: false
        }
        if (want) show() else hide()
    }

    @Suppress("DEPRECATION")
    private fun updateForeground() {
        val um = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val ev = um.queryEvents(since, now)
        val e = UsageEvents.Event()
        while (ev.hasNextEvent()) {
            ev.getNextEvent(e)
            when (e.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> current = e.packageName
                UsageEvents.Event.MOVE_TO_BACKGROUND -> if (e.packageName == current) current = null
            }
        }
        since = now - 1500 // تداخل بسيط لتفادي فقدان الأحداث
    }

    private fun show() {
        if (overlay != null) return
        val v = View(this).apply { setBackgroundColor(0x01000000) }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_SECURE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { alpha = 0.8f } // شرط أندرويد 12+ لتمرير اللمس
        try {
            getSystemService(WindowManager::class.java).addView(v, lp)
            overlay = v
        } catch (_: Exception) { }
    }

    private fun hide() {
        overlay?.let {
            try { getSystemService(WindowManager::class.java).removeView(it) } catch (_: Exception) { }
        }
        overlay = null
    }

    override fun onDestroy() {
        handler.removeCallbacks(loop)
        hide()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

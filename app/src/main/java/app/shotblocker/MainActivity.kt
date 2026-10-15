package app.shotblocker

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var status: TextView
    private lateinit var toggle: Button
    private lateinit var mode: SwitchCompat
    private lateinit var pick: Button
    private lateinit var permOverlay: Button
    private lateinit var permUsage: Button

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun button(label: String, fill: Int, textColor: Int, onClick: () -> Unit) =
        Button(this).apply {
            text = label
            isAllCaps = false
            setTextColor(textColor)
            background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(fill) }
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(12) }
        }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.statusBarColor = C.BG
        window.navigationBarColor = C.BG

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(22), dp(40), dp(22), dp(24))
            setBackgroundColor(C.BG)
        }
        root.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_logo)
            layoutParams = LinearLayout.LayoutParams(dp(110), dp(110))
        })
        root.addView(TextView(this).apply {
            text = getString(R.string.app_name); textSize = 24f
            setTextColor(C.TEXT); gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(4))
        })
        status = TextView(this).apply { textSize = 16f; gravity = Gravity.CENTER }
        root.addView(status)

        toggle = button("", C.ACCENT, C.BG) { onToggle() }
        root.addView(toggle)

        mode = SwitchCompat(this).apply {
            text = getString(R.string.mode_all)
            setTextColor(C.TEXT)
            setPadding(0, dp(18), 0, dp(6))
            setOnCheckedChangeListener { _, v ->
                Prefs.setAllApps(this@MainActivity, v); refresh()
            }
        }
        root.addView(mode, ViewGroup.LayoutParams(-1, -2))

        pick = button(getString(R.string.pick), C.CARD, C.TEXT) {
            startActivity(Intent(this, PickerActivity::class.java))
        }
        root.addView(pick)

        permOverlay = button("", C.CARD, C.TEXT) {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
        }
        permUsage = button("", C.CARD, C.TEXT) {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        root.addView(permOverlay)
        root.addView(permUsage)

        root.addView(TextView(this).apply {
            text = getString(R.string.privacy); textSize = 13f
            setTextColor(C.MUTED); gravity = Gravity.CENTER
            setPadding(0, dp(24), 0, 0)
        })

        setContentView(ScrollView(this).apply {
            isFillViewport = true; setBackgroundColor(C.BG); addView(root)
        })

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
    }

    override fun onResume() { super.onResume(); refresh() }

    private fun hasUsage(): Boolean {
        val ops = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        @Suppress("DEPRECATION")
        return ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName) ==
            AppOpsManager.MODE_ALLOWED
    }

    private fun needUsage() = !Prefs.allApps(this)

    private fun refresh() {
        val on = Prefs.enabled(this)
        status.text = getString(if (on) R.string.status_on else R.string.status_off)
        status.setTextColor(if (on) C.ACCENT else C.MUTED)
        toggle.text = getString(if (on) R.string.btn_off else R.string.btn_on)
        mode.setOnCheckedChangeListener(null)
        mode.isChecked = Prefs.allApps(this)
        mode.setOnCheckedChangeListener { _, v -> Prefs.setAllApps(this, v); refresh() }
        pick.isEnabled = !Prefs.allApps(this)
        pick.alpha = if (pick.isEnabled) 1f else 0.4f

        val o = Settings.canDrawOverlays(this)
        permOverlay.text = (if (o) "✓ " else "") + getString(R.string.perm_overlay)
        val u = hasUsage()
        permUsage.text = (if (u) "✓ " else "") + getString(R.string.perm_usage)
        permUsage.visibility = if (needUsage()) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun onToggle() {
        if (Prefs.enabled(this)) {
            Prefs.setEnabled(this, false)
            stopService(Intent(this, ShieldService::class.java))
        } else {
            if (!Settings.canDrawOverlays(this) || (needUsage() && !hasUsage())) {
                Toast.makeText(this, R.string.need_perms, Toast.LENGTH_LONG).show()
                return
            }
            Prefs.setEnabled(this, true)
            ContextCompat.startForegroundService(this, Intent(this, ShieldService::class.java))
        }
        refresh()
    }
}

object C {
    val BG = Color.parseColor("#0B1220")
    val CARD = Color.parseColor("#16203A")
    val ACCENT = Color.parseColor("#14B8A6")
    val TEXT = Color.parseColor("#E6EDF7")
    val MUTED = Color.parseColor("#7C8AA5")
}

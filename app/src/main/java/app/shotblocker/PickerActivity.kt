package app.shotblocker

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class PickerActivity : AppCompatActivity() {

    private class Item(val pkg: String, val label: String, val icon: Drawable)

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.statusBarColor = C.BG
        window.navigationBarColor = C.BG
        title = getString(R.string.picker_title)

        val list = ListView(this).apply {
            setBackgroundColor(C.BG)
            divider = null
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(C.BG)
            addView(TextView(context).apply {
                text = getString(R.string.picker_title); textSize = 20f
                setTextColor(C.TEXT); setPadding(dp(20), dp(36), dp(20), dp(12))
            })
            addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        setContentView(root)

        Thread {
            val pm = packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val items = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
                .map { it.activityInfo.applicationInfo }
                .distinctBy { it.packageName }
                .filter { it.packageName != packageName }
                .map { Item(it.packageName, it.loadLabel(pm).toString(), it.loadIcon(pm)) }
                .sortedBy { it.label.lowercase() }
            runOnUiThread { list.adapter = Adapter(items) }
        }.start()
    }

    private inner class Adapter(val items: List<Item>) : BaseAdapter() {
        override fun getCount() = items.size
        override fun getItem(i: Int) = items[i]
        override fun getItemId(i: Int) = i.toLong()
        override fun getView(i: Int, v: View?, p: ViewGroup?): View {
            val it = items[i]
            val cb = (v as? CheckBox) ?: CheckBox(this@PickerActivity).apply {
                setTextColor(C.TEXT); textSize = 16f
                setPadding(dp(20), dp(10), dp(20), dp(10))
                compoundDrawablePadding = dp(14)
                buttonTintList = android.content.res.ColorStateList.valueOf(C.ACCENT)
            }
            val ic = it.icon.constantState?.newDrawable() ?: it.icon
            ic.setBounds(0, 0, dp(36), dp(36))
            cb.setCompoundDrawables(ic, null, null, null)
            cb.text = it.label
            cb.setOnCheckedChangeListener(null)
            cb.isChecked = it.pkg in Prefs.packages(this@PickerActivity)
            cb.setOnCheckedChangeListener { _, on -> Prefs.setPackage(this@PickerActivity, it.pkg, on) }
            return cb
        }
    }
}

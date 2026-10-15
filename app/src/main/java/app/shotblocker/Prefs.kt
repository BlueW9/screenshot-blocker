package app.shotblocker

import android.content.Context

/** إعدادات فقط (مفعّل؟ كل التطبيقات؟ التطبيقات المختارة). لا سجل ولا تاريخ. */
object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun enabled(c: Context) = sp(c).getBoolean("enabled", false)
    fun setEnabled(c: Context, v: Boolean) = sp(c).edit().putBoolean("enabled", v).apply()

    fun allApps(c: Context) = sp(c).getBoolean("all", false)
    fun setAllApps(c: Context, v: Boolean) = sp(c).edit().putBoolean("all", v).apply()

    fun packages(c: Context): Set<String> =
        sp(c).getStringSet("pkgs", emptySet())?.toSet() ?: emptySet()

    fun setPackage(c: Context, pkg: String, on: Boolean) {
        val s = packages(c).toMutableSet()
        if (on) s.add(pkg) else s.remove(pkg)
        sp(c).edit().putStringSet("pkgs", s).apply()
    }
}

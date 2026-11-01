package com.friends.ships.ui

import android.content.Context

/** Oddiy sozlamalar xotirasi (SharedPreferences). */
class AppPrefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("ships", Context.MODE_PRIVATE)

    var name: String
        get() = sp.getString("name", "") ?: ""
        set(v) = sp.edit().putString("name", v).apply()

    var sound: Boolean
        get() = sp.getBoolean("sound", true)
        set(v) = sp.edit().putBoolean("sound", v).apply()

    var vibration: Boolean
        get() = sp.getBoolean("vibration", true)
        set(v) = sp.edit().putBoolean("vibration", v).apply()

    /** 0 = tizim, 1 = och, 2 = to'q */
    var theme: Int
        get() = sp.getInt("theme", 2)
        set(v) = sp.edit().putInt("theme", v).apply()

    /** "" = tizim tili, yoki uz / ru / en */
    var lang: String
        get() = sp.getString("lang", "") ?: ""
        set(v) = sp.edit().putString("lang", v).apply()
}

package com.example.gboardclone

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration

object Prefs {
    private const val NAME = "gboardclone_prefs"
    private lateinit var sp: SharedPreferences

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
    }

    /** 0 = light, 1 = dark, 2 = system */
    var themeMode: Int
        get() = sp.getInt(KEY_THEME_MODE, 0)
        set(v) = sp.edit().putInt(KEY_THEME_MODE, v).apply()

    val isDarkNow: Boolean
        get() = when (themeMode) {
            1 -> true
            2 -> (Resources.getSystem().configuration.uiMode
                    and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            else -> false
        }

    var isDark: Boolean
        get() = isDarkNow
        set(v) = themeMode = if (v) 1 else 0

    var numberRow: Boolean
        get() = sp.getBoolean(KEY_NUMBER_ROW, false)
        set(v) = sp.edit().putBoolean(KEY_NUMBER_ROW, v).apply()

    var vibration: Boolean
        get() = sp.getBoolean(KEY_VIBRATION, true)
        set(v) = sp.edit().putBoolean(KEY_VIBRATION, v).apply()

    var sound: Boolean
        get() = sp.getBoolean(KEY_SOUND, false)
        set(v) = sp.edit().putBoolean(KEY_SOUND, v).apply()

    var toolbarCollapsed: Boolean
        get() = sp.getBoolean(KEY_TOOLBAR_COLLAPSED, false)
        set(v) = sp.edit().putBoolean(KEY_TOOLBAR_COLLAPSED, v).apply()

    var heightScale: Float
        get() = sp.getFloat(KEY_HEIGHT_SCALE, 1.0f)
        set(v) = sp.edit().putFloat(KEY_HEIGHT_SCALE, v).apply()

    var emojiRecents: List<String>
        get() = sp.getString(KEY_EMOJI_RECENTS, "")
            ?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()
        set(v) = sp.edit().putString(KEY_EMOJI_RECENTS, v.joinToString(",")).apply()

    var clipboard: List<String>
        get() = sp.getString(KEY_CLIPBOARD, "")
            ?.split("\u0001")?.filter { it.isNotEmpty() } ?: emptyList()
        set(v) = sp.edit().putString(KEY_CLIPBOARD, v.joinToString("\u0001")).apply()

    var pinned: List<String>
        get() = sp.getString(KEY_PINNED, "")
            ?.split("\u0001")?.filter { it.isNotEmpty() } ?: emptyList()
        set(v) = sp.edit().putString(KEY_PINNED, v.joinToString("\u0001")).apply()

    fun togglePin(text: String) {
        val list = pinned.toMutableList()
        if (list.contains(text)) list.remove(text) else list.add(0, text)
        pinned = list
    }

    var learnedWords: Set<String>
        get() = sp.getStringSet(KEY_LEARNED, emptySet()) ?: emptySet()
        set(v) = sp.edit().putStringSet(KEY_LEARNED, v).apply()

    fun addClipboard(text: String) {
        if (text.isBlank()) return
        val list = clipboard.toMutableList()
        list.remove(text)
        list.add(0, text)
        clipboard = list.take(20)
    }

    fun addEmojiRecent(emoji: String) {
        val list = emojiRecents.toMutableList()
        list.remove(emoji)
        list.add(0, emoji)
        emojiRecents = list.take(60)
    }

    private const val KEY_DARK = "dark"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_NUMBER_ROW = "number_row"
    private const val KEY_VIBRATION = "vibration"
    private const val KEY_SOUND = "sound"
    private const val KEY_TOOLBAR_COLLAPSED = "toolbar_collapsed"
    private const val KEY_HEIGHT_SCALE = "height_scale"
    private const val KEY_EMOJI_RECENTS = "emoji_recents"
    private const val KEY_CLIPBOARD = "clipboard"
    private const val KEY_PINNED = "pinned"
    private const val KEY_LEARNED = "learned"
}

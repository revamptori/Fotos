package com.fotos.app

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.fotos.app.engine.RustEngine

class FotosApp : Application() {
    override fun onCreate() {
        super.onCreate()
        RustEngine.nativeInit(filesDir.absolutePath, cacheDir.absolutePath)
        applySavedTheme(this)
    }

    companion object {
        private const val PREFS_NAME = "fotos_prefs"
        const val KEY_THEME = "theme_mode"
        const val KEY_SORT_ASC = "sort_asc"
        const val KEY_ALBUM_SORT = "album_sort"

        fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        fun applySavedTheme(context: Context) {
            // Dark theme by default (MODE_NIGHT_YES = 2, MODE_NIGHT_NO = 1, MODE_NIGHT_FOLLOW_SYSTEM = -1)
            val mode = prefs(context).getInt(KEY_THEME, AppCompatDelegate.MODE_NIGHT_YES)
            AppCompatDelegate.setDefaultNightMode(mode)
        }
    }
}

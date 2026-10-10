package io.github.gap19.aacker.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/** The few user choices; the service follows changes immediately (same process). */
object Settings {
    /** Decode on the audio DSP. On Bluetooth this watch offloads AAC only without gapless joins. */
    const val SAVER = "battery_saver"

    fun prefs(context: Context): SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    fun saver(context: Context) = prefs(context).getBoolean(SAVER, false)
    fun setSaver(context: Context, on: Boolean) = prefs(context).edit { putBoolean(SAVER, on) }
}

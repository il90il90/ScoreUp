package com.scoreup.shared

import android.content.Context
import java.util.UUID

/** Stable, random per-install id used to author events (never derived from hardware ids). */
object DeviceIdentity {
    fun get(context: Context, role: String): String {
        val prefs = context.getSharedPreferences("scoreup_identity", Context.MODE_PRIVATE)
        prefs.getString("id", null)?.let { return it }
        val id = "$role-" + UUID.randomUUID().toString().take(8)
        prefs.edit().putString("id", id).apply()
        return id
    }
}

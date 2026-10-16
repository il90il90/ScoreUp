package com.scoreup.app.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import com.scoreup.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Checks the public releases repo for a newer phone APK (which also carries the newest watch
 * APK) and downloads + hands it to the system installer. No token: the releases repo is public.
 */
class AppUpdater(private val context: Context) {
    data class Release(val versionCode: Long, val versionName: String, val apkUrl: String)

    suspend fun check(): Release? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL(MANIFEST_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = 8_000; conn.readTimeout = 8_000
            conn.setRequestProperty("Cache-Control", "no-cache")
            conn.inputStream.use { parse(it.readBytes().decodeToString()) }
        }.getOrNull()?.takeIf { it.versionCode > BuildConfig.VERSION_CODE }
    }

    /** Downloads with the system DownloadManager (progress in the notification shade), then opens the installer. */
    fun downloadAndInstall(r: Release) {
        val dm = context.getSystemService(DownloadManager::class.java)
        val req = DownloadManager.Request(Uri.parse(r.apkUrl))
            .setTitle("ScoreUp ${r.versionName}")
            .setMimeType(APK_MIME)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "ScoreUp-${r.versionName}.apk")
        val id = dm.enqueue(req)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                if (intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) != id) return
                runCatching { c.unregisterReceiver(this) }
                val uri = dm.getUriForDownloadedFile(id) ?: return
                c.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, APK_MIME)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        else ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    companion object {
        const val RELEASES_REPO = "il90il90/scoreup-releases"
        const val MANIFEST_URL = "https://raw.githubusercontent.com/$RELEASES_REPO/main/latest.json"
        const val APK_MIME = "application/vnd.android.package-archive"

        fun parse(json: String): Release? = runCatching {
            val o = JSONObject(json)
            Release(o.getLong("versionCode"), o.getString("versionName"), o.getString("phoneApkUrl"))
        }.getOrNull()
    }
}

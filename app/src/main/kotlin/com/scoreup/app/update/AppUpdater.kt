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
 * Remote updates from this project's public GitHub Releases. The phone APK carries the matching
 * watch APK, so updating the phone also delivers the newest watch app (installed on the watch
 * with one tap from the ⌚ screen; the scoreboard shows a banner when the watch is behind).
 */
class AppUpdater(private val context: Context) {
    data class Release(val versionCode: Long, val versionName: String, val apkUrl: String, val notes: String = "")

    sealed interface Result {
        data class Available(val release: Release) : Result
        data object UpToDate : Result
        data object Failed : Result
    }

    suspend fun check(): Release? = (checkDetailed() as? Result.Available)?.release

    suspend fun checkDetailed(): Result = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL(LATEST_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = 8_000; conn.readTimeout = 8_000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.inputStream.use { parse(it.readBytes().decodeToString()) }
        }.fold(
            onSuccess = { r -> if (r != null && r.versionCode > BuildConfig.VERSION_CODE) Result.Available(r) else if (r != null) Result.UpToDate else Result.Failed },
            onFailure = { Result.Failed },
        )
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
        const val REPO = "il90il90/ScoreUp"
        const val LATEST_URL = "https://api.github.com/repos/$REPO/releases/latest"
        const val RELEASES_PAGE = "https://github.com/$REPO/releases/latest"
        const val APK_MIME = "application/vnd.android.package-archive"

        /**
         * Parses a GitHub "latest release" response. Tags are v<major.minor.N> and the build sets
         * versionCode = 1000 + N, so the code is derived from the tag's last component.
         */
        fun parse(json: String): Release? = runCatching {
            val o = JSONObject(json)
            val name = o.getString("tag_name").removePrefix("v")
            val build = name.substringAfterLast('.').toLong()
            val assets = o.getJSONArray("assets")
            var url: String? = null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.getString("name").startsWith("ScoreUp-phone-") && a.getString("name").endsWith(".apk")) url = a.getString("browser_download_url")
            }
            Release(1000 + build, name, url ?: return null, o.optString("body"))
        }.getOrNull()
    }
}

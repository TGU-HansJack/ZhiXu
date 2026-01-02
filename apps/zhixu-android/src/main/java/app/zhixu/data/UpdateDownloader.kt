package app.zhixu.data

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.core.content.ContextCompat

object UpdateDownloader {
    private const val APK_MIME = "application/vnd.android.package-archive"

    fun downloadApkAndInstall(
        context: Context,
        url: String,
        version: String,
    ): Long? {
        val appContext = context.applicationContext
        val dm = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: return null

        val request =
            DownloadManager.Request(Uri.parse(url))
                .setTitle("Zhixu")
                .setDescription("Downloading $version")
                .setMimeType(APK_MIME)
                .addRequestHeader("User-Agent", "Zhixu-Android")
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
                .setAllowedNetworkTypes(
                    DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE,
                )
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                .setVisibleInDownloadsUi(true)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "zhixu.apk")

        val downloadId = runCatching { dm.enqueue(request) }.getOrNull() ?: return null
        registerAutoInstallReceiver(appContext, dm, downloadId)
        return downloadId
    }

    private fun registerAutoInstallReceiver(
        context: Context,
        dm: DownloadManager,
        downloadId: Long,
    ) {
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)

        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                    if (id != downloadId) return

                    runCatching { context.unregisterReceiver(this) }

                    val status = queryStatus(dm, downloadId)
                    if (status != DownloadManager.STATUS_SUCCESSFUL) {
                        Toast.makeText(context, "Download failed.", Toast.LENGTH_SHORT).show()
                        return
                    }

                    val uri = dm.getUriForDownloadedFile(downloadId)
                    if (uri == null) {
                        Toast.makeText(context, "Download completed, but file not found.", Toast.LENGTH_SHORT).show()
                        return
                    }

                    val installIntent =
                        Intent(Intent.ACTION_VIEW)
                            .setDataAndType(uri, APK_MIME)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

                    runCatching { context.startActivity(installIntent) }.onFailure {
                        Toast.makeText(context, "Download completed. Open Downloads to install.", Toast.LENGTH_SHORT).show()
                        runCatching {
                            context.startActivity(
                                Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    }
                }
            }

        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    private fun queryStatus(dm: DownloadManager, downloadId: Long): Int? {
        val query = DownloadManager.Query().setFilterById(downloadId)
        dm.query(query)?.use { cursor ->
            if (!cursor.moveToFirst()) return null
            val idx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
            if (idx < 0) return null
            return cursor.getInt(idx)
        }
        return null
    }
}

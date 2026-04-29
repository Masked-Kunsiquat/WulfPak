package com.github.maskedkunisquat.wulfpak.download

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import com.github.maskedkunisquat.wulfpak.core.logic.llm.ModelDownloader
import java.io.File

class DownloadManagerModelDownloader(private val context: Context) : ModelDownloader {
    override fun enqueue(modelFile: String, url: String, sha256: String?) {
        val dest = File(context.filesDir, modelFile)
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("WulfPak: $modelFile")
            .setDescription("On-device AI model")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationUri(Uri.fromFile(dest))
        (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
    }
}

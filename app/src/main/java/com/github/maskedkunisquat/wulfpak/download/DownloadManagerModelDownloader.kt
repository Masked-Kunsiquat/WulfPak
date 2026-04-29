package com.github.maskedkunisquat.wulfpak.download

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import com.github.maskedkunisquat.wulfpak.core.logic.llm.ModelDownloader

class DownloadManagerModelDownloader(private val context: Context) : ModelDownloader {
    override fun enqueue(modelFile: String, url: String, sha256: String?) {
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("WulfPak: $modelFile")
            .setDescription("On-device AI model")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, null, modelFile)
        (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
    }
}

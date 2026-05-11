package com.github.maskedkunisquat.wulfpak

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.WorkManager
import com.github.maskedkunisquat.wulfpak.worker.CallLogImportWorker
import com.github.maskedkunisquat.wulfpak.worker.ContactReminderWorker

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "com.sec.android.intent.action.QUICK_BOOT_POWERON"
        ) return
        val wm = WorkManager.getInstance(context)
        ContactReminderWorker.schedule(wm)
        CallLogImportWorker.schedule(wm)
    }
}

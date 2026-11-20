/*
 * Copyright (C) 2026  Kuldeep Kumar Yadav
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.shub39.grit.core.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import com.shub39.grit.R
import com.shub39.grit.app.ChargingFocusActivity
import com.shub39.grit.core.interfaces.SettingsDatastore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

class ChargingFocusReceiver : BroadcastReceiver(), KoinComponent {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                if (action == Intent.ACTION_POWER_DISCONNECTED) {
                    NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
                    return@launch
                }

                if (action != Intent.ACTION_POWER_CONNECTED) return@launch
                if (!get<SettingsDatastore>().getChargingFocusFlow().first()) return@launch

                if (
                    ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.POST_NOTIFICATIONS,
                    ) != PackageManager.PERMISSION_GRANTED
                ) return@launch

                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    manager.createNotificationChannel(
                        NotificationChannel(
                            CHANNEL_ID,
                            "Charging Focus",
                            NotificationManager.IMPORTANCE_DEFAULT,
                        ),
                    )
                }

                val openFocus = PendingIntent.getActivity(
                    context,
                    NOTIFICATION_ID,
                    Intent(context, ChargingFocusActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.notif_icon)
                    .setContentTitle("Take a break while charging")
                    .setContentText("Tap to open Charging Focus. Your phone stays available for emergencies.")
                    .setContentIntent(openFocus)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .build()
                NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val CHANNEL_ID = "charging_focus"
        private const val NOTIFICATION_ID = 6042
    }
}

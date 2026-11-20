/*
 * Copyright (C) 2026  Kuldeep Kumar Yadav
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.shub39.grit.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

class ChargingFocusActivity : FragmentActivity() {
    private val unplugReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_POWER_DISCONNECTED) finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Remove PIN data saved by earlier Charging Focus versions; this mode is now voluntary.
        getSharedPreferences("charging_focus_security", Context.MODE_PRIVATE).edit().clear().apply()
        val batteryStatus = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) == 0) {
            finish()
            return
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = android.graphics.Color.BLACK
        window.navigationBarColor = android.graphics.Color.BLACK

        ContextCompat.registerReceiver(
            this,
            unplugReceiver,
            IntentFilter(Intent.ACTION_POWER_DISCONNECTED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        setContent {
            ChargingFocusScreen(
                onEmergencyCall = {
                    startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:")))
                },
            )
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(unplugReceiver) }
        super.onDestroy()
    }
}

@Composable
private fun ChargingFocusScreen(onEmergencyCall: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF101820)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.charging_focus_artwork),
            contentDescription = "A peaceful devotional painting of Radha and Krishna",
            modifier = Modifier.weight(1f).fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Charging Focus",
                color = Color.White,
                fontSize = 26.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Let your phone rest while it charges. Take a moment for yourself.",
                color = Color(0xFFE5E7EB),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onEmergencyCall,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black,
                ),
            ) { Text("Emergency phone call") }
        }
    }
}

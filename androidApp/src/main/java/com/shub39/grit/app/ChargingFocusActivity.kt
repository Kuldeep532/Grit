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
import android.os.Bundle
import android.os.BatteryManager
import android.view.WindowManager
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChargingFocusActivity : ComponentActivity() {
    private val preferences by lazy {
        getSharedPreferences("charging_focus_security", Context.MODE_PRIVATE)
    }

    private val unplugReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_POWER_DISCONNECTED) finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
            val scope = rememberCoroutineScope()
            var pin by remember { mutableStateOf("") }
            var confirmation by remember { mutableStateOf("") }
            var message by remember { mutableStateOf("") }
            var hasPin by remember { mutableStateOf(preferences.contains("pin_hash")) }
            var unlocked by remember { mutableStateOf(false) }
            val fieldColors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedLabelColor = Color.White,
                unfocusedLabelColor = Color.LightGray,
                focusedBorderColor = Color.White,
                unfocusedBorderColor = Color.Gray,
                cursorColor = Color.White,
            )

            Column(
                modifier = Modifier.fillMaxSize().background(Color.Black).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Charging Focus",
                    color = Color.White,
                    fontSize = 28.sp,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = if (unlocked) {
                        "Focus mode is paused. You can return to your phone."
                    } else {
                        "Take a quiet break while your phone charges."
                    },
                    color = Color.LightGray,
                )
                Spacer(Modifier.height(28.dp))

                if (!hasPin) {
                    Text("Create an emergency PIN", color = Color.White)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { if (it.length <= 8 && it.all { character -> character.isDigit() }) pin = it },
                        label = { Text("Create 4 to 8 digit PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        colors = fieldColors,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirmation,
                        onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) confirmation = it },
                        label = { Text("Confirm PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        colors = fieldColors,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            if (pin.length !in 4..8 || pin != confirmation) {
                                message = "Enter the same PIN twice. Use 4 to 8 digits."
                            } else {
                                scope.launch {
                                    val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
                                    val hash = withContext(Dispatchers.Default) { derivePinHash(pin, salt) }
                                    preferences.edit()
                                        .putString("pin_salt", Base64.encodeToString(salt, Base64.NO_WRAP))
                                        .putString("pin_hash", Base64.encodeToString(hash, Base64.NO_WRAP))
                                        .apply()
                                    hasPin = true
                                    pin = ""
                                    confirmation = ""
                                    message = "Emergency PIN saved on this device."
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                    ) { Text("Save emergency PIN") }
                } else if (!unlocked) {
                    Text("Enter your emergency PIN to leave focus mode.", color = Color.White)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) pin = it },
                        label = { Text("Emergency PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        colors = fieldColors,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                val salt = Base64.decode(preferences.getString("pin_salt", "") ?: "", Base64.NO_WRAP)
                                val expected = Base64.decode(preferences.getString("pin_hash", "") ?: "", Base64.NO_WRAP)
                                val actual = withContext(Dispatchers.Default) { derivePinHash(pin, salt) }
                                if (MessageDigest.isEqual(expected, actual)) {
                                    unlocked = true
                                    message = "Focus mode unlocked."
                                    finish()
                                } else {
                                    pin = ""
                                    message = "That PIN did not match. Please try again."
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                    ) { Text("Unlock with PIN") }
                }

                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = {
                        startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:")))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray, contentColor = Color.White),
                ) { Text("Emergency phone call") }

                if (message.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(message, color = Color.LightGray)
                }
            }
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(unplugReceiver) }
        super.onDestroy()
    }

    private fun derivePinHash(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 150_000, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}

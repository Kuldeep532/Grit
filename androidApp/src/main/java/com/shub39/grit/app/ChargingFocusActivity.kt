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
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.fragment.app.FragmentActivity
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChargingFocusActivity : FragmentActivity() {
    private val preferences by lazy {
        getSharedPreferences("charging_focus_security", Context.MODE_PRIVATE)
    }

    private lateinit var focusBackCallback: OnBackPressedCallback

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

        focusBackCallback = object : OnBackPressedCallback(preferences.contains("pin_hash")) {
            override fun handleOnBackPressed() {
                // Android Home and system navigation remain available by design.
            }
        }
        onBackPressedDispatcher.addCallback(this, focusBackCallback)

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
            var failedAttempts by remember {
                mutableStateOf(preferences.getInt("failed_pin_attempts", 0))
            }
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
                modifier = Modifier.fillMaxSize().background(Color(0xFF101820)).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Charging Focus", color = Color.White, fontSize = 28.sp)
                Spacer(Modifier.height(12.dp))
                Text(
                    text = if (unlocked) {
                        "Focus mode is paused."
                    } else {
                        "Take a calm break while your phone charges. Use your emergency PIN to leave this screen."
                    },
                    color = Color.LightGray,
                )
                Spacer(Modifier.height(28.dp))

                if (!hasPin) {
                    Text("Create an emergency PIN", color = Color.White)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pin,
                        onValueChange = {
                            if (it.length <= 8 && it.all(Char::isDigit)) pin = it
                        },
                        label = { Text("Create 4 to 8 digit PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        colors = fieldColors,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirmation,
                        onValueChange = {
                            if (it.length <= 8 && it.all(Char::isDigit)) confirmation = it
                        },
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
                                    savePin(pin)
                                    hasPin = true
                                    failedAttempts = 0
                                    focusBackCallback.isEnabled = true
                                    pin = ""
                                    confirmation = ""
                                    message = "Emergency PIN saved on this device."
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black,
                        ),
                    ) { Text("Save emergency PIN") }
                } else if (!unlocked) {
                    Text("Enter your emergency PIN to leave focus mode.", color = Color.White)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = pin,
                        onValueChange = {
                            if (it.length <= 8 && it.all(Char::isDigit)) pin = it
                        },
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
                                val salt = Base64.decode(
                                    preferences.getString("pin_salt", "") ?: "",
                                    Base64.NO_WRAP,
                                )
                                val expected = Base64.decode(
                                    preferences.getString("pin_hash", "") ?: "",
                                    Base64.NO_WRAP,
                                )
                                val actual = withContext(Dispatchers.Default) {
                                    derivePinHash(pin, salt)
                                }
                                if (MessageDigest.isEqual(expected, actual)) {
                                    preferences.edit().putInt("failed_pin_attempts", 0).apply()
                                    failedAttempts = 0
                                    unlocked = true
                                    message = "Focus mode unlocked."
                                    finish()
                                } else {
                                    failedAttempts += 1
                                    preferences.edit()
                                        .putInt("failed_pin_attempts", failedAttempts)
                                        .apply()
                                    pin = ""
                                    message = if (failedAttempts >= 5) {
                                        "Five attempts used. You can reset your PIN with your phone's screen lock."
                                    } else {
                                        "That PIN did not match. Attempts: $failedAttempts of 5."
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black,
                        ),
                    ) { Text("Unlock with PIN") }

                    if (failedAttempts >= 5) {
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                                    .setTitle("Reset Charging Focus PIN")
                                    .setSubtitle("Confirm your phone's screen lock to create a new PIN.")
                                    .apply {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                            setAllowedAuthenticators(
                                                androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
                                            )
                                        } else {
                                            @Suppress("DEPRECATION")
                                            setDeviceCredentialAllowed(true)
                                        }
                                    }
                                    .build()

                                val prompt = BiometricPrompt(
                                    this@ChargingFocusActivity,
                                    ContextCompat.getMainExecutor(this@ChargingFocusActivity),
                                    object : BiometricPrompt.AuthenticationCallback() {
                                        override fun onAuthenticationSucceeded(
                                            result: BiometricPrompt.AuthenticationResult
                                        ) {
                                            super.onAuthenticationSucceeded(result)
                                            preferences.edit()
                                                .remove("pin_salt")
                                                .remove("pin_hash")
                                                .putInt("failed_pin_attempts", 0)
                                                .apply()
                                            failedAttempts = 0
                                            hasPin = false
                                            pin = ""
                                            confirmation = ""
                                            focusBackCallback.isEnabled = false
                                            message = "Screen lock verified. Create your new Charging Focus PIN."
                                        }

                                        override fun onAuthenticationError(
                                            errorCode: Int,
                                            errString: CharSequence
                                        ) {
                                            super.onAuthenticationError(errorCode, errString)
                                            message = "Screen lock verification was not completed. Your existing PIN is unchanged."
                                        }
                                    },
                                )
                                prompt.authenticate(promptInfo)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color.Black,
                            ),
                        ) { Text("Reset with Screen Lock") }
                    }
                }

                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = {
                        startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:")))
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.DarkGray,
                        contentColor = Color.White,
                    ),
                ) { Text("Emergency phone call") }

                if (message.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(message, color = Color.LightGray)
                }
            }
        }
    }

    private suspend fun savePin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = withContext(Dispatchers.Default) { derivePinHash(pin, salt) }
        preferences.edit()
            .putString("pin_salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("pin_hash", Base64.encodeToString(hash, Base64.NO_WRAP))
            .putInt("failed_pin_attempts", 0)
            .apply()
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

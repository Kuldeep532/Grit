/*
 * Copyright (C) 2026  Kuldeep Kumar Yadav
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.shub39.grit.app

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.shub39.grit.core.data.ContentFilterBlocklist
import com.shub39.grit.core.data.ContentProtectionVpnService
import kotlinx.coroutines.launch

class ContentProtectionActivity : ComponentActivity() {
    private val vpnPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) startProtectionService()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val scope = rememberCoroutineScope()
            var isUpdating by remember { mutableStateOf(false) }
            var message by remember { mutableStateOf("Download the protection list before turning protection on.") }
            var downloadedCount by remember { mutableStateOf(0) }
            var isActive by remember { mutableStateOf(false) }

            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text("Content Protection", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(12.dp))
                Text(
                    "Block many known adult website domains on this phone. Protection uses an on-device list and Android's local VPN permission.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(16.dp))
                Text(message, style = MaterialTheme.typography.bodyMedium)
                if (downloadedCount > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text("$downloadedCount domains are ready on this device.")
                }
                Spacer(Modifier.height(20.dp))
                Button(
                    enabled = !isUpdating,
                    onClick = {
                        scope.launch {
                            isUpdating = true
                            message = "Downloading the latest protection list…"
                            runCatching { ContentFilterBlocklist.update(this@ContentProtectionActivity) }
                                .onSuccess {
                                    downloadedCount = it.blockedDomainCount
                                    message = "The protection list is ready."
                                }
                                .onFailure {
                                    message = "The list could not be updated. Check your internet connection and try again."
                                }
                            isUpdating = false
                        }
                    },
                ) { Text(if (isUpdating) "Updating…" else "Update protection list") }
                Spacer(Modifier.height(8.dp))
                Button(
                    enabled = !isUpdating,
                    onClick = {
                        if (!ContentFilterBlocklist.hasDownloadedList(this@ContentProtectionActivity)) {
                            message = "Please download the protection list first."
                        } else {
                            val permissionIntent = VpnService.prepare(this@ContentProtectionActivity)
                            if (permissionIntent == null) {
                                startProtectionService()
                                isActive = true
                                message = "Content Protection has been started."
                            } else {
                                vpnPermissionLauncher.launch(permissionIntent)
                            }
                        }
                    },
                ) { Text("Turn on protection") }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        startService(
                            Intent(this@ContentProtectionActivity, ContentProtectionVpnService::class.java)
                                .setAction(ContentProtectionVpnService.ACTION_STOP),
                        )
                        isActive = false
                        message = "Content Protection has been turned off."
                    },
                ) { Text("Turn off protection") }
                Spacer(Modifier.height(20.dp))
                Text(
                    "Important: this first version filters IPv4 UDP DNS requests only. Some apps and websites can bypass domain filtering using encrypted DNS, their own network connection, or other methods. It does not inspect images or videos. Only one VPN can be active at a time.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }

    private fun startProtectionService() {
        ContextCompat.startForegroundService(
            this,
            Intent(this, ContentProtectionVpnService::class.java)
                .setAction(ContentProtectionVpnService.ACTION_START),
        )
    }
}

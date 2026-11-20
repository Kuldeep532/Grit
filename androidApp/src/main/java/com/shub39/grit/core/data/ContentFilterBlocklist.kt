/*
 * Copyright (C) 2026  Kuldeep Kumar Yadav
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.shub39.grit.core.data

import android.content.Context
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Downloads and stores the community-maintained adult-domain list for the
 * upcoming on-device Content Protection feature.
 *
 * The source list is licensed CC BY-SA 4.0. Keep attribution with any
 * redistributed copy. This class only prepares domain data; it is not itself
 * a network filter and does not claim to block traffic.
 */
object ContentFilterBlocklist {
    private const val META_URL =
        "https://raw.githubusercontent.com/Bon-Appetit/porn-domains/main/meta.json"
    private const val LIST_FILE = "content_filter_domains.txt"
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 45_000

    @Volatile
    private var cachedDomains: Set<String>? = null

    data class UpdateResult(
        val blockedDomainCount: Int,
        val updatedAt: String,
    )

    suspend fun update(context: Context): UpdateResult = withContext(Dispatchers.IO) {
        val metadata = JSONObject(downloadText(META_URL))
        val blockInfo = metadata.getJSONObject("blocklist")
        val allowInfo = metadata.getJSONObject("allowlist")
        val blockUrl = blockInfo.getString("raw_url")
        val allowUrl = allowInfo.getString("raw_url")

        val allowed = parseDomains(downloadText(allowUrl))
        val blocked = parseDomains(downloadText(blockUrl))
            .filterNot { domain -> allowed.any { allowedDomain ->
                domain == allowedDomain || domain.endsWith(".$allowedDomain")
            } }
            .toSortedSet()

        require(blocked.isNotEmpty()) { "The downloaded domain list was empty." }

        val destination = File(context.filesDir, LIST_FILE)
        val temporary = File(context.filesDir, "$LIST_FILE.tmp")
        temporary.bufferedWriter(Charsets.UTF_8).use { writer ->
            blocked.forEach { domain ->
                writer.append(domain)
                writer.newLine()
            }
        }
        if (!temporary.renameTo(destination)) {
            temporary.copyTo(destination, overwrite = true)
            temporary.delete()
        }

        cachedDomains = blocked
        UpdateResult(
            blockedDomainCount = blocked.size,
            updatedAt = blockInfo.optString("updated", "Unknown"),
        )
    }

    fun isBlocked(context: Context, hostname: String): Boolean {
        val host = normalizeDomain(hostname) ?: return false
        val domains = cachedDomains ?: loadDomains(context).also { cachedDomains = it }
        var candidate = host
        while (true) {
            if (candidate in domains) return true
            val dot = candidate.indexOf('.')
            if (dot < 0) return false
            candidate = candidate.substring(dot + 1)
        }
    }

    fun hasDownloadedList(context: Context): Boolean =
        File(context.filesDir, LIST_FILE).isFile

    private fun loadDomains(context: Context): Set<String> {
        val file = File(context.filesDir, LIST_FILE)
        if (!file.isFile) return emptySet()
        return file.useLines { lines ->
            lines.mapNotNull(::normalizeDomain).toSet()
        }
    }

    private fun downloadText(address: String): String {
        val connection = URL(address).openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.setRequestProperty("User-Agent", "NexusLife-ContentProtection/1.0")
        return try {
            require(connection.responseCode in 200..299) {
                "The content protection list could not be downloaded."
            }
            BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).use {
                it.readText()
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun parseDomains(text: String): Set<String> =
        text.lineSequence().mapNotNull { line ->
            val entry = line.substringBefore('#').trim()
            if (entry.isEmpty()) return@mapNotNull null
            val candidate = entry.split(Regex("\\s+")).lastOrNull() ?: return@mapNotNull null
            normalizeDomain(candidate)
        }.toSet()

    private fun normalizeDomain(value: String): String? {
        val candidate = value.trim().trimEnd('.').lowercase(Locale.ROOT)
        if (candidate.isEmpty() || candidate.length > 253 || candidate.contains('/')) return null
        if (candidate.any { it.isWhitespace() || it == ':' }) return null
        val labels = candidate.split('.')
        if (labels.size < 2) return null
        if (labels.any { label ->
            label.isEmpty() || label.length > 63 ||
                label.first() == '-' || label.last() == '-' ||
                label.any { !(it.isLetterOrDigit() || it == '-') }
        }) return null
        return candidate
    }
}

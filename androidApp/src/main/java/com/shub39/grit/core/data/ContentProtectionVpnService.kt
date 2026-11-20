/*
 * Copyright (C) 2026  Kuldeep Kumar Yadav
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.shub39.grit.core.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * DNS-only local VPN.
 *
 * Only packets addressed to the virtual DNS address are routed into this
 * interface. Other Internet traffic is not routed through this service.
 * This first implementation handles IPv4 UDP DNS. It intentionally does not
 * claim to filter DNS-over-HTTPS, DNS-over-TLS, IPv6 DNS, or TCP DNS fallback.
 */
class ContentProtectionVpnService : VpnService() {
    private var tunnel: ParcelFileDescriptor? = null
    private var worker: Thread? = null
    private val running = AtomicBoolean(false)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopFiltering()
                stopSelf()
                return START_NOT_STICKY
            }
        }

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        if (!running.get()) startFiltering()
        return START_STICKY
    }

    private fun startFiltering() {
        val descriptor = Builder()
            .setSession("Nexus Life Content Protection")
            .setMtu(1500)
            .addAddress(VPN_ADDRESS, 32)
            .addDnsServer(DNS_ADDRESS)
            .addRoute(DNS_ADDRESS, 32)
            .establish() ?: run {
                stopSelf()
                return
            }

        tunnel = descriptor
        running.set(true)
        worker = thread(name = "NexusLife-DNS-Filter", isDaemon = true) {
            val input = FileInputStream(descriptor.fileDescriptor)
            val output = FileOutputStream(descriptor.fileDescriptor)
            val packet = ByteArray(32767)

            while (running.get()) {
                try {
                    val length = input.read(packet)
                    if (length <= 0) continue
                    val response = handleDnsPacket(packet.copyOf(length))
                    if (response != null && running.get()) {
                        synchronized(output) {
                            output.write(response)
                            output.flush()
                        }
                    }
                } catch (_: Exception) {
                    if (running.get()) {
                        // A transient malformed or timed-out DNS packet must not crash the app.
                    }
                }
            }
        }
    }

    private fun handleDnsPacket(packet: ByteArray): ByteArray? {
        if (packet.size < 28) return null
        val ipVersion = ((packet[0].toInt() and 0xFF) ushr 4) and 0x0F
        if (ipVersion != 4) return null
        val ipHeaderLength = (packet[0].toInt() and 0x0F) * 4
        if (ipHeaderLength < 20 || packet.size < ipHeaderLength + 8) return null
        if ((packet[9].toInt() and 0xFF) != 17) return null // UDP only

        val udpOffset = ipHeaderLength
        val sourcePort = readU16(packet, udpOffset)
        val destinationPort = readU16(packet, udpOffset + 2)
        if (destinationPort != 53) return null

        val udpLength = readU16(packet, udpOffset + 4)
        if (udpLength < 8 || packet.size < udpOffset + udpLength) return null
        val dnsOffset = udpOffset + 8
        val dnsQuery = packet.copyOfRange(dnsOffset, udpOffset + udpLength)
        val host = readQuestionName(dnsQuery) ?: return null

        val dnsResponse = if (ContentFilterBlocklist.isBlocked(this, host)) {
            buildBlockedResponse(dnsQuery)
        } else {
            forwardDns(dnsQuery) ?: return null
        }

        val sourceAddress = packet.copyOfRange(12, 16)
        val destinationAddress = packet.copyOfRange(16, 20)
        return buildIpv4UdpPacket(
            sourceAddress = destinationAddress,
            destinationAddress = sourceAddress,
            sourcePort = destinationPort,
            destinationPort = sourcePort,
            payload = dnsResponse,
        )
    }

    private fun forwardDns(query: ByteArray): ByteArray? {
        val socket = DatagramSocket()
        return try {
            if (!protect(socket)) return null
            socket.soTimeout = 5_000
            val server = InetAddress.getByName(UPSTREAM_DNS)
            socket.send(DatagramPacket(query, query.size, server, 53))
            val response = ByteArray(4096)
            val reply = DatagramPacket(response, response.size)
            socket.receive(reply)
            response.copyOf(reply.length)
        } catch (_: SocketTimeoutException) {
            null
        } catch (_: Exception) {
            null
        } finally {
            socket.close()
        }
    }

    private fun readQuestionName(dns: ByteArray): String? {
        if (dns.size < 17) return null
        val labels = ArrayList<String>()
        var cursor = 12
        while (cursor < dns.size) {
            val size = dns[cursor++].toInt() and 0xFF
            if (size == 0) break
            if (size > 63 || cursor + size > dns.size) return null
            labels += String(dns, cursor, size, Charsets.US_ASCII)
            cursor += size
        }
        if (labels.isEmpty() || cursor + 4 > dns.size) return null
        return labels.joinToString(".").lowercase()
    }

    private fun buildBlockedResponse(query: ByteArray): ByteArray {
        val questionEnd = findQuestionEnd(query) ?: return query
        val response = query.copyOf(questionEnd)
        response[2] = 0x81.toByte()
        response[3] = 0x83.toByte() // response, recursion desired/available, NXDOMAIN
        response[6] = 0
        response[7] = 0
        response[8] = 0
        response[9] = 0
        response[10] = 0
        response[11] = 0
        return response
    }

    private fun findQuestionEnd(query: ByteArray): Int? {
        var cursor = 12
        while (cursor < query.size) {
            val size = query[cursor++].toInt() and 0xFF
            if (size == 0) break
            if (size > 63 || cursor + size > query.size) return null
            cursor += size
        }
        if (cursor + 4 > query.size) return null
        return cursor + 4
    }

    private fun buildIpv4UdpPacket(
        sourceAddress: ByteArray,
        destinationAddress: ByteArray,
        sourcePort: Int,
        destinationPort: Int,
        payload: ByteArray,
    ): ByteArray {
        val udpLength = 8 + payload.size
        val totalLength = 20 + udpLength
        val result = ByteArray(totalLength)
        val buffer = ByteBuffer.wrap(result).order(ByteOrder.BIG_ENDIAN)
        buffer.put(0, 0x45.toByte())
        buffer.put(1, 0)
        buffer.putShort(2, totalLength.toShort())
        buffer.putShort(4, 0)
        buffer.putShort(6, 0)
        buffer.put(8, 64)
        buffer.put(9, 17)
        buffer.putShort(10, 0)
        System.arraycopy(sourceAddress, 0, result, 12, 4)
        System.arraycopy(destinationAddress, 0, result, 16, 4)
        buffer.putShort(10, checksum(result, 0, 20).toShort())

        buffer.putShort(20, sourcePort.toShort())
        buffer.putShort(22, destinationPort.toShort())
        buffer.putShort(24, udpLength.toShort())
        buffer.putShort(26, 0)
        System.arraycopy(payload, 0, result, 28, payload.size)
        return result
    }

    private fun checksum(bytes: ByteArray, offset: Int, length: Int): Int {
        var sum = 0L
        var index = offset
        while (index + 1 < offset + length) {
            sum += (((bytes[index].toInt() and 0xFF) shl 8) or
                (bytes[index + 1].toInt() and 0xFF)).toLong()
            index += 2
        }
        if (index < offset + length) sum += ((bytes[index].toInt() and 0xFF) shl 8).toLong()
        while ((sum ushr 16) != 0L) sum = (sum and 0xFFFF) + (sum ushr 16)
        return sum.inv().toInt() and 0xFFFF
    }

    private fun readU16(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 8) or
            (bytes[offset + 1].toInt() and 0xFF)

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Content Protection",
                NotificationManager.IMPORTANCE_LOW,
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("Nexus Life Content Protection")
            .setContentText("DNS protection is active on this device.")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

    private fun stopFiltering() {
        running.set(false)
        runCatching { tunnel?.close() }
        tunnel = null
        worker = null
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onRevoke() {
        stopFiltering()
        stopSelf()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopFiltering()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.nexuslife.contentprotection.START"
        const val ACTION_STOP = "com.nexuslife.contentprotection.STOP"
        private const val CHANNEL_ID = "content_protection"
        private const val NOTIFICATION_ID = 7301
        private const val VPN_ADDRESS = "10.111.0.1"
        private const val DNS_ADDRESS = "10.111.0.2"
        private const val UPSTREAM_DNS = "1.1.1.1"
    }
}

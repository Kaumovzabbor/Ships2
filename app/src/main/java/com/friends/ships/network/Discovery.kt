package com.friends.ships.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

/** Android NSD (mDNS): xonani e'lon qilish va qidirish. */
class Discovery(ctx: Context) {
    companion object {
        const val TYPE = "_ships._tcp."
        const val PREFIX = "Ships-"
    }

    private val nsd = ctx.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var reg: NsdManager.RegistrationListener? = null
    private var disc: NsdManager.DiscoveryListener? = null
    private val queue = ArrayDeque<NsdServiceInfo>()
    private var resolving = false

    fun register(name: String, port: Int) {
        unregister()
        val info = NsdServiceInfo().apply {
            serviceName = PREFIX + name
            serviceType = TYPE
            setPort(port)
        }
        val l = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(i: NsdServiceInfo) {}
            override fun onRegistrationFailed(i: NsdServiceInfo, e: Int) {}
            override fun onServiceUnregistered(i: NsdServiceInfo) {}
            override fun onUnregistrationFailed(i: NsdServiceInfo, e: Int) {}
        }
        reg = l
        try {
            nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, l)
        } catch (_: Exception) {
        }
    }

    fun unregister() {
        reg?.let {
            try {
                nsd.unregisterService(it)
            } catch (_: Exception) {
            }
        }
        reg = null
    }

    fun discover(onFound: (String, String, Int) -> Unit, onLost: (String) -> Unit) {
        stopDiscovery()
        val l = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(t: String) {}
            override fun onDiscoveryStopped(t: String) {}
            override fun onStartDiscoveryFailed(t: String, e: Int) {}
            override fun onStopDiscoveryFailed(t: String, e: Int) {}
            override fun onServiceFound(s: NsdServiceInfo) {
                if (!s.serviceName.startsWith(PREFIX)) return
                synchronized(queue) { queue.add(s) }
                next(onFound)
            }

            override fun onServiceLost(s: NsdServiceInfo) {
                onLost(s.serviceName)
            }
        }
        disc = l
        try {
            nsd.discoverServices(TYPE, NsdManager.PROTOCOL_DNS_SD, l)
        } catch (_: Exception) {
        }
    }

    fun stopDiscovery() {
        disc?.let {
            try {
                nsd.stopServiceDiscovery(it)
            } catch (_: Exception) {
            }
        }
        disc = null
        synchronized(queue) { queue.clear() }
    }

    private fun next(onFound: (String, String, Int) -> Unit) {
        val s = synchronized(queue) {
            if (resolving || queue.isEmpty()) {
                null
            } else {
                resolving = true
                queue.removeFirst()
            }
        } ?: return
        try {
            @Suppress("DEPRECATION")
            nsd.resolveService(s, object : NsdManager.ResolveListener {
                override fun onResolveFailed(i: NsdServiceInfo, e: Int) = done(onFound)
                override fun onServiceResolved(i: NsdServiceInfo) {
                    i.host?.hostAddress?.let { onFound(i.serviceName, it, i.port) }
                    done(onFound)
                }
            })
        } catch (e: Exception) {
            done(onFound)
        }
    }

    private fun done(onFound: (String, String, Int) -> Unit) {
        synchronized(queue) { resolving = false }
        next(onFound)
    }
}

/** Telefonning Wi-Fi / hotspot IPv4 manzili. */
fun localIp(): String = try {
    Collections.list(NetworkInterface.getNetworkInterfaces())
        .filter { it.isUp && !it.isLoopback }
        .flatMap { Collections.list(it.inetAddresses) }
        .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
        ?.hostAddress ?: "?"
} catch (e: Exception) {
    "?"
}

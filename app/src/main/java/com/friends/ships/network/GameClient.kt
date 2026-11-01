package com.friends.ships.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

class GameClient(private val scope: CoroutineScope) {
    private var sock: Socket? = null
    private val outQ = Channel<Msg>(Channel.UNLIMITED)

    /** Server xabarlari; uzilganda "DISCONNECTED" keladi. */
    val incoming = Channel<Msg>(Channel.UNLIMITED)

    suspend fun connect(host: String, port: Int, name: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val s = Socket()
            s.connect(InetSocketAddress(host, port), 4000)
            s.keepAlive = true
            s.tcpNoDelay = true
            sock = s
            val w = s.getOutputStream().bufferedWriter()
            scope.launch(Dispatchers.IO) {
                try {
                    for (m in outQ) {
                        w.write(json.encodeToString(Msg.serializer(), m))
                        w.write("\n")
                        w.flush()
                    }
                } catch (_: Exception) {
                }
            }
            scope.launch(Dispatchers.IO) {
                try {
                    val r = s.getInputStream().bufferedReader()
                    while (true) {
                        val line = r.readLine() ?: break
                        val m = try {
                            json.decodeFromString(Msg.serializer(), line)
                        } catch (e: Exception) {
                            null
                        }
                        if (m != null) incoming.trySend(m)
                    }
                } catch (_: Exception) {
                }
                incoming.trySend(Msg("DISCONNECTED"))
            }
            send(Msg("JOIN", name = name))
            true
        } catch (e: Exception) {
            false
        }
    }

    fun send(m: Msg) {
        outQ.trySend(m)
    }

    fun close() {
        outQ.close()
        try {
            sock?.close()
        } catch (_: Exception) {
        }
    }
}

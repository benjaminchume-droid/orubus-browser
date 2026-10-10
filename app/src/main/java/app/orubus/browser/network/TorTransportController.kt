package app.orubus.browser.network

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import org.torproject.jni.TorService
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Owns the embedded Tor daemon lifecycle and reports observed transport state.
 *
 * CONNECTED means Tor reports an established circuit, the configured SOCKS port
 * is listening, and a SOCKS5 no-auth handshake succeeds. It does not claim that
 * every browser traffic class has passed leak testing.
 */
class TorTransportController(
    context: Context,
    private val onStateChanged: (State) -> Unit
) {
    enum class State { OFF, STARTING, CONNECTED, FAILED, STOPPING }

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "orubus-tor-verification").apply { isDaemon = true }
    }

    @Volatile private var torService: TorService? = null
    @Volatile private var currentState = State.OFF
    private var bound = false
    private var receiverRegistered = false
    private var pollTask: ScheduledFuture<*>? = null
    private var startDeadlineMs = 0L

    val state: State get() = currentState

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                TorService.ACTION_ERROR -> transition(State.FAILED)
                TorService.ACTION_STATUS -> when (intent.getStringExtra(TorService.EXTRA_STATUS)) {
                    TorService.STATUS_STARTING -> transition(State.STARTING)
                    TorService.STATUS_ON -> verifyNow()
                    TorService.STATUS_STOPPING, TorService.STATUS_OFF -> {
                        if (currentState != State.STOPPING && currentState != State.OFF) {
                            transition(State.FAILED)
                        }
                    }
                }
            }
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            torService = (binder as? TorService.LocalBinder)?.service
            if (torService == null) {
                transition(State.FAILED)
                return
            }
            scheduleVerification(0)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            torService = null
            if (currentState != State.STOPPING && currentState != State.OFF) {
                transition(State.FAILED)
            }
        }
    }

    @Synchronized
    fun start() {
        if (currentState == State.STARTING || currentState == State.CONNECTED) return
        transition(State.STARTING)
        startDeadlineMs = System.currentTimeMillis() + START_TIMEOUT_MS
        try {
            writeTorConfiguration()
            if (!receiverRegistered) {
                val filter = IntentFilter().apply {
                    addAction(TorService.ACTION_STATUS)
                    addAction(TorService.ACTION_ERROR)
                }
                appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
                receiverRegistered = true
            }
            val intent = Intent(appContext, TorService::class.java).setAction(TorService.ACTION_START)
            appContext.startService(intent)
            bound = appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            if (!bound) transition(State.FAILED)
            else scheduleVerification(500)
        } catch (_: Exception) {
            transition(State.FAILED)
        }
    }

    @Synchronized
    fun stop() {
        transition(State.STOPPING)
        pollTask?.cancel(true)
        pollTask = null
        try {
            if (bound) appContext.unbindService(connection)
        } catch (_: IllegalArgumentException) {
            // Already unbound by the Android service lifecycle.
        }
        bound = false
        torService = null
        try {
            appContext.stopService(Intent(appContext, TorService::class.java))
        } catch (_: Exception) {
            // The browser still treats Tor as off; the next start performs fresh verification.
        }
        if (receiverRegistered) {
            try {
                appContext.unregisterReceiver(receiver)
            } catch (_: IllegalArgumentException) {
                // Receiver was already removed.
            }
            receiverRegistered = false
        }
        transition(State.OFF)
    }

    fun close() {
        stop()
        executor.shutdownNow()
    }

    private fun writeTorConfiguration() {
        val torrc = TorService.getTorrc(appContext)
        torrc.parentFile?.let { if (!it.exists() && !it.mkdirs()) error("Cannot create Tor config directory") }
        // Keep the SOCKS endpoint stable so every browser context can be configured
        // before Tor starts. The endpoint binds only to loopback, never the LAN.
        torrc.writeText(
            "SocksPort 127.0.0.1:$SOCKS_PORT\n" +
                "SafeSocks 1\n" +
                "ClientOnly 1\n"
        )
    }

    private fun scheduleVerification(delayMs: Long) {
        if (currentState == State.OFF || currentState == State.STOPPING) return
        pollTask?.cancel(false)
        pollTask = executor.schedule({
            verifyTransport()
        }, delayMs, TimeUnit.MILLISECONDS)
    }

    private fun verifyNow() = scheduleVerification(0)

    private fun verifyTransport() {
        val service = torService
        if (service == null) {
            if (System.currentTimeMillis() >= startDeadlineMs) transition(State.FAILED)
            scheduleVerification(POLL_INTERVAL_MS)
            return
        }

        val port = runCatching { service.socksPort }.getOrDefault(-1)
        val circuitEstablished = runCatching {
            service.getInfo("status/circuit-established")?.trim() == "1"
        }.getOrDefault(false)
        val socksReady = port == SOCKS_PORT && verifySocksHandshake(port)

        when {
            circuitEstablished && socksReady -> transition(State.CONNECTED)
            currentState == State.CONNECTED -> transition(State.FAILED)
            System.currentTimeMillis() >= startDeadlineMs -> transition(State.FAILED)
        }
        scheduleVerification(POLL_INTERVAL_MS)
    }

    private fun verifySocksHandshake(port: Int): Boolean = try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress("127.0.0.1", port), SOCKET_TIMEOUT_MS)
            socket.soTimeout = SOCKET_TIMEOUT_MS
            socket.getOutputStream().write(byteArrayOf(0x05, 0x01, 0x00))
            socket.getOutputStream().flush()
            val version = socket.getInputStream().read()
            val method = socket.getInputStream().read()
            version == 0x05 && method == 0x00
        }
    } catch (_: Exception) {
        false
    }

    private fun transition(next: State) {
        if (currentState == next) return
        currentState = next
        mainHandler.post { onStateChanged(next) }
    }

    companion object {
        const val SOCKS_PORT = 39050
        private const val SOCKET_TIMEOUT_MS = 1_500
        private const val POLL_INTERVAL_MS = 2_000L
        private const val START_TIMEOUT_MS = 180_000L
    }
}

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
import java.nio.charset.StandardCharsets
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.HttpsURLConnection
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
        // A local SOCKS handshake alone only proves the listener exists. The
        // end-to-end check asks Tor Project's check endpoint through SOCKS and
        // requires its IsTor=true response before showing the route as verified.
        val torExitVerified = circuitEstablished && socksReady && verifyTorExit(port)

        when {
            torExitVerified -> transition(State.CONNECTED)
            currentState == State.CONNECTED -> transition(State.FAILED)
            System.currentTimeMillis() >= startDeadlineMs -> transition(State.FAILED)
        }
        scheduleVerification(if (currentState == State.CONNECTED) CONNECTED_POLL_INTERVAL_MS else POLL_INTERVAL_MS)
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

    /**
     * Makes a TLS request through SOCKS5 using a domain-name CONNECT target, so
     * the destination hostname is sent to Tor rather than resolved by Android.
     * The response must explicitly identify the observed exit as a Tor exit.
     */
    private fun verifyTorExit(port: Int): Boolean = try {
        val host = "check.torproject.org"
        Socket().use { raw ->
            raw.connect(InetSocketAddress("127.0.0.1", port), SOCKET_TIMEOUT_MS)
            raw.soTimeout = EXIT_CHECK_TIMEOUT_MS
            val input = raw.getInputStream()
            val output = raw.getOutputStream()
            output.write(byteArrayOf(0x05, 0x01, 0x00))
            output.flush()
            if (input.read() != 0x05 || input.read() != 0x00) return false

            val hostBytes = host.toByteArray(StandardCharsets.US_ASCII)
            output.write(byteArrayOf(0x05, 0x01, 0x00, 0x03, hostBytes.size.toByte()) +
                hostBytes + byteArrayOf(0x01, 0xBB.toByte()))
            output.flush()

            val version = input.read()
            val reply = input.read()
            input.read() // reserved
            val addressType = input.read()
            if (version != 0x05 || reply != 0x00) return false
            when (addressType) {
                0x01 -> repeat(4) { if (input.read() < 0) return false }
                0x03 -> {
                    val length = input.read()
                    if (length < 0) return false
                    repeat(length) { if (input.read() < 0) return false }
                }
                0x04 -> repeat(16) { if (input.read() < 0) return false }
                else -> return false
            }
            if (input.read() < 0 || input.read() < 0) return false // bound port

            val tls = (SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket(raw, host, 443, true) as SSLSocket
            tls.use { secure ->
                secure.soTimeout = EXIT_CHECK_TIMEOUT_MS
                secure.startHandshake()
                if (!HttpsURLConnection.getDefaultHostnameVerifier().verify(host, secure.session)) return false
                val request = "GET /api/ip HTTP/1.0\r\n" +
                    "Host: $host\r\nConnection: close\r\nUser-Agent: OrubusTorVerifier/1\r\n\r\n"
                secure.getOutputStream().write(request.toByteArray(StandardCharsets.US_ASCII))
                secure.getOutputStream().flush()
                val response = secure.getInputStream().bufferedReader(StandardCharsets.UTF_8).readText()
                val statusLine = response.lineSequence().firstOrNull().orEmpty()
                if (!statusLine.matches(Regex("HTTP/1\\.[01] 200(?: .*)?"))) return false
                val body = response.substringAfter("\r\n\r\n", "")
                Regex("\"IsTor\"\\s*:\\s*true", RegexOption.IGNORE_CASE).containsMatchIn(body)
            }
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
        private const val CONNECTED_POLL_INTERVAL_MS = 30_000L
        private const val EXIT_CHECK_TIMEOUT_MS = 8_000
        private const val START_TIMEOUT_MS = 180_000L
    }
}

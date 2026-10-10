package app.orubus.browser.network

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import java.io.ByteArrayInputStream
import java.io.File
import java.security.KeyStore
import java.util.concurrent.Executors
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * WireGuard userspace tunnel controller.
 *
 * Configurations are accepted only when they provide IPv4 and IPv6 default routes
 * and do not restrict routing to selected applications. This is intentionally a
 * full-tunnel browser-device policy, not a split-tunnel profile manager.
 */
class WireGuardTransportController(
    context: Context,
    private val onStateChanged: (State, String?) -> Unit
) {
    enum class State { OFF, CONFIGURED, CONNECTING, CONNECTED, FAILED, STOPPING }

    private val appContext = context.applicationContext
    private val worker = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "orubus-wireguard").apply { isDaemon = true }
    }
    @Volatile private var currentState = if (hasSavedConfig()) State.CONFIGURED else State.OFF
    @Volatile private var closed = false
    @Volatile private var lastError: String? = null
    private val configFile = File(appContext.filesDir, CONFIG_FILE)
    private val tunnel = object : Tunnel {
        override fun getName(): String = TUNNEL_NAME
        override fun onStateChange(newState: Tunnel.State) {
            when (newState) {
                Tunnel.State.UP -> transition(State.CONNECTED)
                Tunnel.State.DOWN -> if (currentState != State.STOPPING) transition(
                    if (hasSavedConfig()) State.CONFIGURED else State.OFF
                )
                Tunnel.State.TOGGLE -> Unit
            }
        }
    }

    val state: State get() = currentState
    val error: String? get() = lastError
    fun hasSavedConfig(): Boolean = configFile.exists() && configFile.length() > 0

    /** Returns Android's consent Intent when VPN permission has not yet been granted. */
    fun permissionIntent(): Intent? = VpnService.prepare(appContext)

    /** Validates a full-tunnel config before encrypting and storing it in app-private storage. */
    @Synchronized
    fun saveConfig(text: String) {
        require(text.isNotBlank()) { "Paste a WireGuard configuration first." }
        require(text.length <= MAX_CONFIG_CHARS) { "Configuration is unexpectedly large." }
        val config = parseAndValidate(text)
        // Parsing validates syntax and route policy before secrets are written.
        require(config.peers.isNotEmpty()) { "The configuration must contain at least one peer." }
        val encrypted = encrypt(text.toByteArray(Charsets.UTF_8))
        val temporary = File(appContext.filesDir, "$CONFIG_FILE.tmp")
        temporary.writeBytes(encrypted)
        if (configFile.exists() && !configFile.delete()) {
            temporary.delete()
            error("Could not replace the saved WireGuard configuration.")
        }
        if (!temporary.renameTo(configFile)) {
            temporary.delete()
            error("Could not safely save the encrypted WireGuard configuration.")
        }
        lastError = null
        transition(State.CONFIGURED)
    }

    @Synchronized
    fun removeConfig() {
        if (currentState == State.CONNECTED || currentState == State.CONNECTING) {
            error("Disconnect WireGuard before removing its configuration.")
        }
        if (configFile.exists() && !configFile.delete()) {
            error("Could not remove the saved WireGuard configuration.")
        }
        lastError = null
        transition(State.OFF)
    }

    fun start() {
        if (closed) return
        if (!hasSavedConfig()) {
            fail("Add a WireGuard configuration before connecting.")
            return
        }
        if (permissionIntent() != null) {
            fail("Android VPN permission has not been granted.")
            return
        }
        transition(State.CONNECTING)
        worker.execute {
            try {
                val plaintext = decrypt(configFile.readBytes()).toString(Charsets.UTF_8)
                val config = parseAndValidate(plaintext)
                val backend = GoBackend(appContext)
                backend.setState(tunnel, Tunnel.State.UP, config)
                if (backend.getState(tunnel) != Tunnel.State.UP) {
                    throw IllegalStateException("WireGuard backend did not report the tunnel as up.")
                }
                lastError = null
                transition(State.CONNECTED)
            } catch (e: Exception) {
                fail(e.message?.takeIf { it.isNotBlank() } ?: "WireGuard could not start.")
            }
        }
    }

    fun stop() {
        if (closed) return
        transition(State.STOPPING)
        worker.execute {
            try {
                // GoBackend owns the process-level tunnel service. A fresh backend
                // instance can stop its own active tunnel only when it owns that state;
                // the singleton backend below keeps tunnel ownership stable.
                val backend = getBackend()
                if (backend.getState(tunnel) == Tunnel.State.UP) {
                    backend.setState(tunnel, Tunnel.State.DOWN, null)
                }
                lastError = null
                transition(if (hasSavedConfig()) State.CONFIGURED else State.OFF)
            } catch (e: Exception) {
                fail(e.message?.takeIf { it.isNotBlank() } ?: "WireGuard could not stop cleanly.")
            }
        }
    }

    fun close() {
        closed = true
        // Do not silently disconnect a user-requested VPN when the Activity is recreated.
        // The OS VPN service owns the live tunnel after activation.
        worker.shutdown()
    }

    private fun parseAndValidate(text: String): Config {
        val config = Config.parse(ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)))
        require(config.peers.isNotEmpty()) { "The configuration needs at least one [Peer] section." }
        require(config.interface.includedApplications.isEmpty() &&
            config.interface.excludedApplications.isEmpty()) {
            "Application-specific routing is not accepted. Remove IncludedApplications and ExcludedApplications."
        }
        val hasIpv4Default = config.peers.any { peer ->
            peer.allowedIps.any { it.mask == 0 && it.address.address.size == 4 }
        }
        val hasIpv6Default = config.peers.any { peer ->
            peer.allowedIps.any { it.mask == 0 && it.address.address.size == 16 }
        }
        require(hasIpv4Default && hasIpv6Default) {
            "For leak-resistant full-tunnel routing, every address family must be covered: add AllowedIPs = 0.0.0.0/0, ::/0 to a peer."
        }
        return config
    }

    private fun encrypt(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext)
        return byteArrayOf(iv.size.toByte()) + iv + ciphertext
    }

    private fun decrypt(payload: ByteArray): ByteArray {
        require(payload.isNotEmpty()) { "Saved WireGuard configuration is empty." }
        val ivLength = payload[0].toInt() and 0xff
        require(ivLength in 12..16 && payload.size > 1 + ivLength) {
            "Saved WireGuard configuration is corrupt. Import it again."
        }
        val iv = payload.copyOfRange(1, 1 + ivLength)
        val ciphertext = payload.copyOfRange(1 + ivLength, payload.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private fun getBackend(): GoBackend = synchronized(Companion) {\n        backendInstance ?: GoBackend(appContext).also { backendInstance = it }\n    }\n\n    private fun handleBackendState(newState: Tunnel.State) {\n        when (newState) {\n            Tunnel.State.UP -> transition(State.CONNECTED)\n            Tunnel.State.DOWN -> if (currentState != State.STOPPING) transition(\n                if (hasSavedConfig()) State.CONFIGURED else State.OFF\n            )\n            Tunnel.State.TOGGLE -> Unit\n        }\n    }\n\n    private fun fail(message: String) {
        lastError = message
        transition(State.FAILED, message)
    }

    private fun transition(next: State, message: String? = null) {
        currentState = next
        onStateChanged(next, message)
    }

    companion object {
        private const val TUNNEL_NAME = "orubus"
        private const val CONFIG_FILE = "wireguard.conf.enc"
        private const val KEY_ALIAS = "orubus.wireguard.config.v1"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val MAX_CONFIG_CHARS = 64 * 1024
        @Volatile private var backendInstance: GoBackend? = null\n        @Volatile private var sharedTunnel: Tunnel? = null\n        @Volatile private var activeController: WireGuardTransportController? = null
    }
}

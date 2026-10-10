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
 * WireGuard userspace tunnel controller. Only full-tunnel configurations covering
 * IPv4 and IPv6 are accepted. App-specific include/exclude routing is rejected.
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
    private val configFile = File(appContext.filesDir, CONFIG_FILE)
    @Volatile private var currentState = if (hasSavedConfig()) State.CONFIGURED else State.OFF
    @Volatile private var closed = false
    @Volatile private var lastError: String? = null
    private val tunnel: Tunnel
        get() = sharedTunnel ?: error("WireGuard tunnel has not been initialized")

    init {
        synchronized(Companion) {
            activeController = this
            if (sharedTunnel == null) {
                sharedTunnel = object : Tunnel {
                    override fun getName(): String = TUNNEL_NAME
                    override fun onStateChange(newState: Tunnel.State) {
                        activeController?.handleBackendState(newState)
                    }
                }
            }
            if (backendInstance != null && sharedTunnel != null &&
                backendInstance!!.getState(sharedTunnel!!) == Tunnel.State.UP
            ) currentState = State.CONNECTED
        }
    }

    val state: State get() = currentState
    val error: String? get() = lastError
    fun hasSavedConfig(): Boolean = configFile.exists() && configFile.length() > 0

    /** Returns Android's VPN-consent Intent if the user has not approved this app. */
    fun permissionIntent(): Intent? = VpnService.prepare(appContext)

    /** Validate first, then encrypt the complete config using an Android Keystore AES key. */
    @Synchronized
    fun saveConfig(text: String) {
        require(text.isNotBlank()) { "Paste a WireGuard configuration first." }
        require(text.length <= MAX_CONFIG_CHARS) { "Configuration is unexpectedly large." }
        parseAndValidate(text)
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
        require(currentState != State.CONNECTED && currentState != State.CONNECTING) {
            "Disconnect WireGuard before removing its configuration."
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
                val backend = getBackend()
                backend.setState(tunnel, Tunnel.State.UP, config)
                check(backend.getState(tunnel) == Tunnel.State.UP) {
                    "WireGuard backend did not report the tunnel as up."
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
        synchronized(Companion) {
            if (activeController === this) activeController = null
        }
        // Do not disconnect a user-requested VPN just because the Activity is recreated.
        worker.shutdown()
    }

    private fun parseAndValidate(text: String): Config {
        val config = Config.parse(ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)))
        require(config.peers.isNotEmpty()) {
            "The configuration needs at least one [Peer] section."
        }
        val iface = config.getInterface()
        require(iface.includedApplications.isEmpty() && iface.excludedApplications.isEmpty()) {
            "Application-specific routing is not accepted. Remove IncludedApplications and ExcludedApplications."
        }
        val hasIpv4Default = config.peers.any { peer ->
            peer.allowedIps.any { it.mask == 0 && it.address.address.size == 4 }
        }
        val hasIpv6Default = config.peers.any { peer ->
            peer.allowedIps.any { it.mask == 0 && it.address.address.size == 16 }
        }
        require(hasIpv4Default && hasIpv6Default) {
            "Full-tunnel policy requires AllowedIPs = 0.0.0.0/0, ::/0 on a peer to cover IPv4 and IPv6."
        }
        return config
    }

    private fun getBackend(): GoBackend = synchronized(Companion) {
        backendInstance ?: GoBackend(appContext).also { backendInstance = it }
    }

    private fun handleBackendState(newState: Tunnel.State) {
        when (newState) {
            Tunnel.State.UP -> transition(State.CONNECTED)
            Tunnel.State.DOWN -> if (currentState != State.STOPPING) {
                transition(if (hasSavedConfig()) State.CONFIGURED else State.OFF)
            }
            Tunnel.State.TOGGLE -> Unit
        }
    }

    private fun encrypt(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        return byteArrayOf(iv.size.toByte()) + iv + cipher.doFinal(plaintext)
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

    private fun fail(message: String) {
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
        @Volatile private var backendInstance: GoBackend? = null
        @Volatile private var sharedTunnel: Tunnel? = null
        @Volatile private var activeController: WireGuardTransportController? = null
    }
}

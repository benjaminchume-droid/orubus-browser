package app.orubus.browser.updates

import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Signed metadata for independently versioned, non-executable update packs.
 *
 * The signature is detached and covers the exact manifest bytes, avoiding ambiguous
 * JSON canonicalization. The public key is pinned by the application build.
 */
data class UpdatePack(
    val id: String,
    val version: Long,
    val url: String,
    val sha256: String,
    val compressedBytes: Long,
    val maxExpandedBytes: Long,
    val minAppVersionCode: Long,
    val maxAppVersionCode: Long,
    val mediaType: String
)

data class VerifiedUpdateManifest(
    val channel: String,
    val generatedAt: String,
    val packs: List<UpdatePack>
)

object UpdateManifestVerifier {
    private val idPattern = Regex("[a-z0-9][a-z0-9._-]{0,63}")
    private val sha256Pattern = Regex("[a-fA-F0-9]{64}")

    /**
     * Verify a detached ECDSA/SHA-256 signature over the raw manifest before parsing it.
     * Public key must be a Base64-encoded X.509 EC public key pinned in the app build.
     */
    fun verifyAndParse(
        manifestBytes: ByteArray,
        detachedSignatureBase64: String,
        pinnedPublicKeyBase64: String,
        appVersionCode: Long,
        allowedHosts: Set<String>
    ): VerifiedUpdateManifest {
        require(manifestBytes.isNotEmpty() && manifestBytes.size <= MAX_MANIFEST_BYTES) {
            "Update manifest is empty or too large."
        }
        require(detachedSignatureBase64.isNotBlank()) { "Update manifest signature is missing." }
        require(pinnedPublicKeyBase64.isNotBlank()) { "No update-signing public key is configured." }

        val publicKeyBytes = Base64.getDecoder().decode(pinnedPublicKeyBase64)
        val publicKey = KeyFactory.getInstance("EC")
            .generatePublic(X509EncodedKeySpec(publicKeyBytes))
        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(publicKey)
        verifier.update(manifestBytes)
        val signatureBytes = Base64.getDecoder().decode(detachedSignatureBase64)
        require(verifier.verify(signatureBytes)) { "Update manifest signature is invalid." }

        val root = JSONObject(String(manifestBytes, StandardCharsets.UTF_8))
        require(root.optInt("schemaVersion", -1) == 1) { "Unsupported update manifest schema." }
        val channel = root.optString("channel", "")
        require(channel in setOf("stable", "beta", "nightly")) { "Unknown update channel." }
        val generatedAt = root.optString("generatedAt", "")
        require(generatedAt.isNotBlank()) { "Manifest generation time is missing." }
        val jsonPacks = root.optJSONArray("packs")
            ?: throw IllegalArgumentException("Manifest has no packs array.")
        require(jsonPacks.length() <= MAX_PACKS) { "Manifest contains too many packs." }

        val packs = (0 until jsonPacks.length()).map { index ->
            val item = jsonPacks.getJSONObject(index)
            val id = item.optString("id", "")
            val version = item.optLong("version", -1)
            val url = item.optString("url", "")
            val sha256 = item.optString("sha256", "")
            val compressedBytes = item.optLong("compressedBytes", -1)
            val maxExpandedBytes = item.optLong("maxExpandedBytes", -1)
            val minAppVersionCode = item.optLong("minAppVersionCode", -1)
            val maxAppVersionCode = item.optLong("maxAppVersionCode", Long.MAX_VALUE)
            val mediaType = item.optString("mediaType", "")

            require(idPattern.matches(id)) { "Invalid update pack identifier." }
            require(version >= 1) { "Pack version must be positive." }
            require(url.startsWith("https://")) { "Update packs must use HTTPS." }
            val host = java.net.URI(url).host?.lowercase()
            require(host != null && allowedHosts.any { it.equals(host, ignoreCase = true) }) {
                "Update pack host is not allowlisted."
            }
            require(sha256Pattern.matches(sha256)) { "Pack SHA-256 must be 64 hexadecimal characters." }
            require(compressedBytes in 1..MAX_PACK_COMPRESSED_BYTES) { "Pack compressed size is outside limits." }
            require(maxExpandedBytes in 1..MAX_PACK_EXPANDED_BYTES) { "Pack expanded size is outside limits." }
            require(minAppVersionCode >= 1 && maxAppVersionCode >= minAppVersionCode) {
                "Pack app-version compatibility range is invalid."
            }
            require(appVersionCode in minAppVersionCode..maxAppVersionCode) {
                "Pack is not compatible with this app version."
            }
            require(mediaType in ALLOWED_MEDIA_TYPES) { "Executable or unsupported update pack type rejected." }
            UpdatePack(id, version, url, sha256.lowercase(), compressedBytes, maxExpandedBytes,
                minAppVersionCode, maxAppVersionCode, mediaType)
        }
        require(packs.map { it.id }.distinct().size == packs.size) { "Duplicate pack identifiers." }
        VerifiedUpdateManifest(channel, generatedAt, packs)
    }

    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private const val MAX_MANIFEST_BYTES = 512 * 1024
    private const val MAX_PACKS = 128
    private const val MAX_PACK_COMPRESSED_BYTES = 100L * 1024 * 1024
    private const val MAX_PACK_EXPANDED_BYTES = 512L * 1024 * 1024
    private val ALLOWED_MEDIA_TYPES = setOf(
        "application/vnd.orubus.resources+zip",
        "application/vnd.orubus.filterlists+zip",
        "application/vnd.orubus.locale+zip",
        "application/vnd.orubus.searchproviders+zip"
    )
}

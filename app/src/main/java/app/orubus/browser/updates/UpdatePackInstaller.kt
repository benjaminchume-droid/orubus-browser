package app.orubus.browser.updates

import android.content.Context
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream
import javax.net.ssl.HttpsURLConnection

/** Installs verified data/resource packs atomically under app-private storage. Call off the UI thread. */
class UpdatePackInstaller(context: Context) {
    private val root = File(context.applicationContext.filesDir, "update-packs")

    fun install(pack: UpdatePack): File {
        require(pack.mediaType in SUPPORTED_TYPES) { "Unsupported update pack type." }
        root.mkdirs()
        val packRoot = File(root, pack.id)
        check(packRoot.canonicalFile.parentFile == root.canonicalFile) { "Invalid pack path." }
        val versionDir = File(packRoot, pack.version.toString())
        val installedHash = File(versionDir, ".installed-sha256")
        if (installedHash.isFile && installedHash.readText().trim().equals(pack.sha256, true)) {
            return versionDir
        }

        val download = File(root, "." + pack.id + "-" + pack.version + ".download")
        val staging = File(root, "." + pack.id + "-" + pack.version + ".staging")
        download.delete()
        deleteRecursively(staging)
        try {
            downloadPack(pack, download)
            check(UpdateManifestVerifier.sha256Hex(download.readBytes()).equals(pack.sha256, true)) {
                "Update pack SHA-256 does not match the signed manifest."
            }
            staging.mkdirs()
            extractSafely(download, staging, pack.maxExpandedBytes)
            File(staging, ".installed-sha256").writeText(pack.sha256)
            File(staging, ".installed-version").writeText(pack.version.toString())
            packRoot.mkdirs()
            if (versionDir.exists()) deleteRecursively(versionDir)
            check(staging.renameTo(versionDir)) { "Could not activate update pack." }
            return versionDir
        } finally {
            download.delete()
            if (staging.exists()) deleteRecursively(staging)
        }
    }

    private fun downloadPack(pack: UpdatePack, destination: File) {
        val connection = URL(pack.url).openConnection() as? HttpsURLConnection
            ?: error("Update URL must use HTTPS.")
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("Accept", pack.mediaType)
            connection.setRequestProperty("User-Agent", "OrubusBrowser-UpdateManager/1")
            val code = connection.responseCode
            check(code == HttpURLConnection.HTTP_OK) {
                if (code in 300..399) "Update server redirects are not accepted."
                else "Update server returned HTTP " + code + "."
            }
            val advertised = connection.contentLengthLong
            if (advertised >= 0) check(advertised == pack.compressedBytes) {
                "Update pack length does not match the signed manifest."
            }
            var total = 0L
            connection.inputStream.use { input ->
                FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        check(total <= pack.compressedBytes && total <= MAX_COMPRESSED_BYTES) {
                            "Downloaded pack exceeds its signed size limit."
                        }
                        output.write(buffer, 0, read)
                    }
                    output.fd.sync()
                }
            }
            check(total == pack.compressedBytes) { "Downloaded pack length is incomplete." }
        } finally {
            connection.disconnect()
        }
    }

    private fun extractSafely(archive: File, destination: File, maxExpandedBytes: Long) {
        var expanded = 0L
        var entries = 0
        ZipInputStream(BufferedInputStream(archive.inputStream())).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries++
                check(entries <= MAX_ENTRIES) { "Update pack has too many entries." }
                val name = entry.name
                check(name.isNotBlank() && !name.startsWith("/") && '\\' !in name) {
                    "Update pack contains an unsafe path."
                }
                val target = File(destination, name).canonicalFile
                check(target.path.startsWith(destination.canonicalPath + File.separator)) {
                    "Update pack contains a path traversal entry."
                }
                check(isAllowedDataFile(name)) {
                    "Update pack contains executable or unsupported content: " + name
                }
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    BufferedOutputStream(FileOutputStream(target)).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = zip.read(buffer)
                            if (read < 0) break
                            expanded += read
                            check(expanded <= maxExpandedBytes && expanded <= MAX_EXPANDED_BYTES) {
                                "Expanded pack exceeds its signed size limit."
                            }
                            output.write(buffer, 0, read)
                        }
                    }
                }
                zip.closeEntry()
            }
        }
        check(entries > 0) { "Update pack is empty." }
    }

    private fun isAllowedDataFile(path: String): Boolean {
        val lower = path.lowercase()
        val blocked = listOf(
            ".apk", ".aab", ".dex", ".jar", ".so", ".class", ".odex", ".vdex",
            ".sh", ".bat", ".cmd", ".ps1", ".exe", ".dll", ".wasm", ".js", ".mjs"
        )
        if (blocked.any { lower.endsWith(it) }) return false
        return lower.endsWith(".json") || lower.endsWith(".txt") || lower.endsWith(".dat") ||
            lower.endsWith(".list") || lower.endsWith(".xml") || lower.endsWith(".png") ||
            lower.endsWith(".webp") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
            lower.endsWith(".svg") || lower.endsWith(".css") || lower.endsWith(".html") ||
            lower.endsWith(".woff") || lower.endsWith(".woff2") || lower.endsWith(".ttf")
    }

    private fun deleteRecursively(file: File) {
        if (file.isDirectory) file.listFiles()?.forEach(::deleteRecursively)
        file.delete()
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000
        private const val MAX_COMPRESSED_BYTES = 100L * 1024 * 1024
        private const val MAX_EXPANDED_BYTES = 512L * 1024 * 1024
        private const val MAX_ENTRIES = 10_000
        private val SUPPORTED_TYPES = setOf(
            "application/vnd.orubus.resources+zip",
            "application/vnd.orubus.filterlists+zip",
            "application/vnd.orubus.locale+zip",
            "application/vnd.orubus.searchproviders+zip"
        )
    }
}

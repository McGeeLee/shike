package com.gee.eatapp.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

data class AppRelease(
    val versionName: String,
    val releaseUrl: String,
    val releaseNotes: String,
    val apkName: String,
    val apkUrl: String,
    val checksumUrl: String,
)

class AppUpdateClient {
    suspend fun latestRelease(currentVersionName: String): AppRelease? = withContext(Dispatchers.IO) {
        val connection = (URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", GITHUB_API_VERSION)
            setRequestProperty("User-Agent", "Shike-Android/$currentVersionName")
        }

        try {
            when (connection.responseCode) {
                HttpURLConnection.HTTP_OK -> parseRelease(
                    connection.inputStream.readUtf8Limited(MAX_RESPONSE_BYTES),
                )

                HttpURLConnection.HTTP_NOT_FOUND -> null
                HTTP_TOO_MANY_REQUESTS, HttpURLConnection.HTTP_FORBIDDEN ->
                    throw IOException("GitHub 暂时限制了版本查询，请稍后再试")

                else -> throw IOException("版本服务暂时不可用（HTTP ${connection.responseCode}）")
            }
        } finally {
            connection.disconnect()
        }
    }

    suspend fun downloadRelease(context: Context, release: AppRelease): File = withContext(Dispatchers.IO) {
        if (release.apkName != "shike-v${release.versionName}.apk" ||
            !isTrustedAssetUrl(release.apkUrl) ||
            !isTrustedAssetUrl(release.checksumUrl)
        ) {
            throw IOException("更新包信息不受信任")
        }
        val updateDirectory = File(context.filesDir, UPDATE_DIRECTORY)
        if (!updateDirectory.exists() && !updateDirectory.mkdirs()) {
            throw IOException("无法创建更新目录")
        }
        val target = File(updateDirectory, release.apkName)
        val partial = File(updateDirectory, "${release.apkName}.part")
        target.delete()
        partial.delete()

        try {
            val checksumPayload = downloadBytes(release.checksumUrl, MAX_CHECKSUM_BYTES)
                .toString(Charsets.UTF_8)
            val expectedChecksum = parseSha256(checksumPayload)
                ?: throw IOException("更新包校验文件无效")
            downloadFile(release.apkUrl, partial, MAX_APK_BYTES)
            val actualChecksum = partial.sha256()
            if (!actualChecksum.equals(expectedChecksum, ignoreCase = true)) {
                throw IOException("更新包完整性校验失败，请重新下载")
            }
            verifyDownloadedPackage(context, partial, release)
            if (!partial.renameTo(target)) {
                partial.copyTo(target, overwrite = true)
                partial.delete()
            }
            target
        } catch (error: Throwable) {
            partial.delete()
            target.delete()
            throw error
        }
    }

    private fun parseRelease(payload: String): AppRelease {
        val json = runCatching { JSONObject(payload) }
            .getOrElse { throw IOException("版本服务返回了无效数据") }
        val tagName = json.optString("tag_name").trim()
        val versionName = tagName.removePrefix("v").removePrefix("V")
        if (parseVersion(versionName) == null) throw IOException("版本号格式无效")

        val releaseUrl = json.optString("html_url").trim()
        if (!isTrustedReleaseUrl(releaseUrl)) throw IOException("版本下载地址无效")

        val expectedApkName = "shike-$tagName.apk"
        val assets = json.optJSONArray("assets")
            ?: throw IOException("发布版本缺少可下载的 APK")
        val assetUrls = buildMap {
            for (index in 0 until assets.length()) {
                val asset = assets.optJSONObject(index) ?: continue
                val name = asset.optString("name").trim()
                val url = asset.optString("browser_download_url").trim()
                if (name.isNotEmpty() && isTrustedAssetUrl(url)) put(name, url)
            }
        }
        val apkUrl = assetUrls[expectedApkName]
            ?: throw IOException("发布版本缺少 $expectedApkName")
        val checksumUrl = assetUrls["$expectedApkName.sha256"]
            ?: throw IOException("发布版本缺少 SHA-256 校验文件")

        return AppRelease(
            versionName = versionName,
            releaseUrl = releaseUrl,
            releaseNotes = json.optString("body").trim().take(MAX_RELEASE_NOTES_CHARS),
            apkName = expectedApkName,
            apkUrl = apkUrl,
            checksumUrl = checksumUrl,
        )
    }

    private fun isTrustedReleaseUrl(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme.equals("https", ignoreCase = true) &&
            uri.host.equals("github.com", ignoreCase = true) &&
            uri.port == -1 &&
            uri.userInfo == null &&
            uri.path.startsWith(RELEASE_PATH_PREFIX)
    }.getOrDefault(false)

    private fun isTrustedAssetUrl(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme.equals("https", ignoreCase = true) &&
            uri.host.equals("github.com", ignoreCase = true) &&
            uri.port == -1 &&
            uri.userInfo == null &&
            uri.path.startsWith(ASSET_PATH_PREFIX)
    }.getOrDefault(false)

    private suspend fun downloadBytes(url: String, maxBytes: Int): ByteArray {
        val connection = openDownloadConnection(url)
        return try {
            connection.inputStream.readBytesLimited(maxBytes)
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun downloadFile(url: String, target: File, maxBytes: Long) {
        val connection = openDownloadConnection(url)
        try {
            val contentLength = connection.contentLengthLong
            if (contentLength > maxBytes) throw IOException("更新包大小超出限制")
            connection.inputStream.use { input ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > maxBytes) throw IOException("更新包大小超出限制")
                        output.write(buffer, 0, count)
                    }
                    output.fd.sync()
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun openDownloadConnection(url: String): HttpURLConnection {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = DOWNLOAD_CONNECT_TIMEOUT_MS
            readTimeout = DOWNLOAD_READ_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/octet-stream")
            setRequestProperty("User-Agent", "Shike-Android-Updater")
        }
        val responseCode = connection.responseCode
        if (responseCode != HttpURLConnection.HTTP_OK) {
            connection.disconnect()
            throw IOException("更新包下载失败（HTTP $responseCode）")
        }
        val finalUri = URI(connection.url.toString())
        val trustedHost = finalUri.host?.lowercase() in TRUSTED_DOWNLOAD_HOSTS
        if (!finalUri.scheme.equals("https", ignoreCase = true) || !trustedHost) {
            connection.disconnect()
            throw IOException("更新包下载地址不受信任")
        }
        return connection
    }

    private fun verifyDownloadedPackage(context: Context, apk: File, release: AppRelease) {
        val packageManager = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        val archive = packageManager.getPackageArchiveInfo(apk.absolutePath, flags)
            ?: throw IOException("下载的文件不是有效 APK")
        val installed = packageManager.getPackageInfo(context.packageName, flags)
        if (archive.packageName != context.packageName || archive.versionName != release.versionName) {
            throw IOException("更新包的应用标识或版本不匹配")
        }
        if (archive.longVersionCodeCompat() <= installed.longVersionCodeCompat()) {
            throw IOException("更新包版本不高于当前版本")
        }
        val installedSigners = installed.signerDigests()
        val archiveSigners = archive.signerDigests()
        if (installedSigners.isEmpty() || installedSigners.intersect(archiveSigners).isEmpty()) {
            throw IOException("更新包签名与当前应用不一致")
        }
    }

    companion object {
        private const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/McGeeLee/shike/releases/latest"
        private const val RELEASE_PATH_PREFIX = "/McGeeLee/shike/releases/"
        private const val ASSET_PATH_PREFIX = "/McGeeLee/shike/releases/download/"
        private const val UPDATE_DIRECTORY = "updates"
        private const val GITHUB_API_VERSION = "2026-03-10"
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 15_000
        private const val MAX_RESPONSE_BYTES = 512 * 1024
        private const val MAX_CHECKSUM_BYTES = 4 * 1024
        private const val MAX_APK_BYTES = 200L * 1024 * 1024
        private const val MAX_RELEASE_NOTES_CHARS = 2_000
        private const val DOWNLOAD_CONNECT_TIMEOUT_MS = 15_000
        private const val DOWNLOAD_READ_TIMEOUT_MS = 60_000
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private val TRUSTED_DOWNLOAD_HOSTS = setOf(
            "github.com",
            "release-assets.githubusercontent.com",
            "objects.githubusercontent.com",
        )
    }
}

class UpdateCheckStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun shouldAutoCheck(now: Long = System.currentTimeMillis()): Boolean {
        val lastAttempt = preferences.getLong(KEY_LAST_ATTEMPT, 0L)
        return lastAttempt <= 0L || now < lastAttempt || now - lastAttempt >= AUTO_CHECK_INTERVAL_MS
    }

    fun recordAttempt(now: Long = System.currentTimeMillis()) {
        preferences.edit().putLong(KEY_LAST_ATTEMPT, now).apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "shike_update_check"
        private const val KEY_LAST_ATTEMPT = "last_attempt_at"
        private const val AUTO_CHECK_INTERVAL_MS = 24 * 60 * 60 * 1_000L
    }
}

internal fun isNewerVersion(latest: String, current: String): Boolean {
    val latestVersion = parseVersion(latest) ?: return false
    val currentVersion = parseVersion(current) ?: return false
    val size = maxOf(latestVersion.parts.size, currentVersion.parts.size)
    for (index in 0 until size) {
        val latestPart = latestVersion.parts.getOrElse(index) { 0 }
        val currentPart = currentVersion.parts.getOrElse(index) { 0 }
        if (latestPart != currentPart) return latestPart > currentPart
    }
    return currentVersion.preRelease && !latestVersion.preRelease
}

private data class ParsedVersion(val parts: List<Int>, val preRelease: Boolean)

private fun parseVersion(value: String): ParsedVersion? {
    val match = VERSION_PATTERN.matchEntire(value.trim()) ?: return null
    val parts = match.groupValues[1].split('.').map { it.toIntOrNull() ?: return null }
    return ParsedVersion(parts, match.groupValues[2].isNotEmpty())
}

private suspend fun InputStream.readUtf8Limited(maxBytes: Int): String {
    return readBytesLimited(maxBytes).toString(Charsets.UTF_8)
}

private suspend fun InputStream.readBytesLimited(maxBytes: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0
    while (true) {
        coroutineContext.ensureActive()
        val count = read(buffer)
        if (count < 0) break
        total += count
        if (total > maxBytes) throw IOException("版本服务返回的数据过大")
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}

internal fun parseSha256(value: String): String? =
    SHA256_PATTERN.find(value)?.groupValues?.get(1)?.lowercase()

private suspend fun File.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    FileInputStream(this).use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            coroutineContext.ensureActive()
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().toHex()
}

@Suppress("DEPRECATION")
private fun PackageInfo.longVersionCodeCompat(): Long =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) longVersionCode else versionCode.toLong()

@Suppress("DEPRECATION")
private fun PackageInfo.signerDigests(): Set<String> {
    val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        signingInfo?.let { info ->
            if (info.hasMultipleSigners()) info.apkContentsSigners else info.signingCertificateHistory
        }
    } else {
        signatures
    }.orEmpty()
    return signatures.mapTo(mutableSetOf()) { signature ->
        MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).toHex()
    }
}

private fun ByteArray.toHex(): String = joinToString("") { byte ->
    "%02x".format(byte.toInt() and 0xff)
}

private val VERSION_PATTERN = Regex(
    pattern = "^[vV]?(\\d+(?:\\.\\d+)*)(?:-([0-9A-Za-z.-]+))?(?:\\+[0-9A-Za-z.-]+)?$",
)

private val SHA256_PATTERN = Regex("(?i)(?:^|\\s)([a-f0-9]{64})(?=\\s|$)")

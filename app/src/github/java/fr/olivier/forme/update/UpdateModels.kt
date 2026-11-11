package fr.olivier.forme.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant

/** update.json of a release of the releases repository (docs/publication.md). */
@Serializable
data class UpdateManifest(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val minSdk: Int = 28,
    val notes: String = "",
)

/** What the GitHub API returns for /releases/latest (the fields used). */
@Serializable
data class GithubRelease(
    @SerialName("tag_name") val tagName: String,
    val assets: List<GithubAsset> = emptyList(),
)

@Serializable
data class GithubAsset(val name: String, @SerialName("browser_download_url") val url: String)

enum class UpdateError { OFFLINE, RATE_LIMITED, NO_RELEASE, INVALID, INCOMPATIBLE, DOWNLOAD, CHECKSUM, INSTALL }

/** The rules of the update (V2.28), apart from Android, for the tests. */
object UpdatePolicy {
    val CHECK_INTERVAL: Duration = Duration.ofHours(24)
    const val MANIFEST_NAME = "update.json"
    private val json = Json { ignoreUnknownKeys = true }

    /** At most once every 24 hours (a clock set back counts as "due"). */
    fun checkDue(lastCheck: Instant?, now: Instant): Boolean =
        lastCheck == null || now.isBefore(lastCheck) || Duration.between(lastCheck, now) >= CHECK_INTERVAL

    /** Never a downgrade, nor the same version. */
    fun isNewer(manifest: UpdateManifest, installedVersionCode: Long): Boolean = manifest.versionCode > installedVersionCode

    fun compatible(manifest: UpdateManifest, sdk: Int): Boolean = sdk >= manifest.minSdk

    /** Downloading: on Wi-Fi (an unmetered network), or anywhere when the setting is off or the user insists. */
    fun mayDownload(wifiOnly: Boolean, unmetered: Boolean, forced: Boolean): Boolean = !wifiOnly || unmetered || forced

    fun parseRelease(text: String): GithubRelease? = runCatching { json.decodeFromString(GithubRelease.serializer(), text) }.getOrNull()

    fun manifestUrl(release: GithubRelease): String? = release.assets.firstOrNull { it.name == MANIFEST_NAME }?.url

    /** A manifest that can be trusted enough to download: https link, 64 hexadecimal digits, a version. */
    fun parseManifest(text: String): UpdateManifest? =
        runCatching { json.decodeFromString(UpdateManifest.serializer(), text) }.getOrNull()?.takeIf {
            it.versionCode > 0 && it.versionName.isNotBlank() && it.apkUrl.startsWith("https://") &&
                it.sha256.matches(Regex("[0-9a-fA-F]{64}"))
        }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun checksumMatches(file: File, expected: String): Boolean = sha256(file).equals(expected.trim(), ignoreCase = true)
}

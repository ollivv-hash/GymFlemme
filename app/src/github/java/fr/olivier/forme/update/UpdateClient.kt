package fr.olivier.forme.update

import fr.olivier.forme.BuildConfig
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

/** Failure of a request, with what to tell the user. */
class UpdateException(val error: UpdateError, cause: Throwable? = null) : Exception(error.name, cause)

/** GitHub, without authentication (60 requests an hour per address: one check a day is far below). */
@Singleton
class UpdateClient @Inject constructor() {
    private fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 30_000
        instanceFollowRedirects = true // GitHub sends the files from another host
        setRequestProperty("User-Agent", "GymFlemme/${BuildConfig.VERSION_NAME} (Android app update check)")
    }

    private fun failure(e: IOException) = UpdateException(if (e is UnknownHostException) UpdateError.OFFLINE else UpdateError.OFFLINE, e)

    private fun text(url: String, accept: String): String {
        val c = open(url).apply { setRequestProperty("Accept", accept) }
        try {
            when (c.responseCode) {
                200 -> return c.inputStream.bufferedReader().use { it.readText() }
                403, 429 -> throw UpdateException(UpdateError.RATE_LIMITED)
                404 -> throw UpdateException(UpdateError.NO_RELEASE)
                else -> throw UpdateException(UpdateError.INVALID)
            }
        } catch (e: IOException) {
            throw failure(e)
        } finally {
            c.disconnect()
        }
    }

    /** The manifest of the latest release of the releases repository. */
    fun latest(): UpdateManifest {
        val release = UpdatePolicy.parseRelease(
            text("https://api.github.com/repos/${BuildConfig.UPDATE_REPOSITORY}/releases/latest", "application/vnd.github+json"),
        ) ?: throw UpdateException(UpdateError.INVALID)
        val manifestUrl = UpdatePolicy.manifestUrl(release) ?: throw UpdateException(UpdateError.INVALID)
        return UpdatePolicy.parseManifest(text(manifestUrl, "application/octet-stream")) ?: throw UpdateException(UpdateError.INVALID)
    }

    /** Downloads [url] into [target], reporting the progress (0 to 1, or null when the size is unknown). */
    fun download(url: String, target: File, onProgress: (Float?) -> Unit) {
        val c = open(url)
        try {
            if (c.responseCode != 200) throw UpdateException(UpdateError.DOWNLOAD)
            val total = c.contentLengthLong.takeIf { it > 0 }
            var done = 0L
            c.inputStream.use { input ->
                target.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        done += n
                        onProgress(total?.let { done.toFloat() / it })
                    }
                }
            }
        } catch (e: IOException) {
            target.delete()
            throw UpdateException(UpdateError.DOWNLOAD, e)
        } finally {
            c.disconnect()
        }
    }
}

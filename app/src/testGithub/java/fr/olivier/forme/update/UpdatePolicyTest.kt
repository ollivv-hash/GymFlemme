package fr.olivier.forme.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant

/** V2.28: the rules of the update, apart from Android and the network. */
class UpdatePolicyTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val manifest = UpdateManifest(
        versionCode = 60, versionName = "2.28.0",
        apkUrl = "https://github.com/ollivv-hash/gymflemme-releases/releases/download/v2.28.0/gymflemme-2.28.0.apk",
        sha256 = "a".repeat(64), minSdk = 28, notes = "Nouveautés",
    )

    @Test
    fun aNewerVersionOnlyNeverADowngrade() {
        assertTrue(UpdatePolicy.isNewer(manifest, 59))
        assertFalse(UpdatePolicy.isNewer(manifest, 60))
        assertFalse(UpdatePolicy.isNewer(manifest, 61))
    }

    @Test
    fun atMostOnceADay() {
        val now = Instant.parse("2026-11-12T08:00:00Z")
        assertTrue(UpdatePolicy.checkDue(null, now))
        assertFalse(UpdatePolicy.checkDue(now.minusSeconds(23 * 3600), now))
        assertTrue(UpdatePolicy.checkDue(now.minusSeconds(24 * 3600), now))
        assertTrue(UpdatePolicy.checkDue(now.plusSeconds(3600), now)) // clock set back
    }

    @Test
    fun wifiRule() {
        assertTrue(UpdatePolicy.mayDownload(wifiOnly = true, unmetered = true, forced = false))
        assertFalse(UpdatePolicy.mayDownload(wifiOnly = true, unmetered = false, forced = false))
        assertTrue(UpdatePolicy.mayDownload(wifiOnly = true, unmetered = false, forced = true)) // "Télécharger quand même"
        assertTrue(UpdatePolicy.mayDownload(wifiOnly = false, unmetered = false, forced = false))
    }

    @Test
    fun minSdk() {
        assertTrue(UpdatePolicy.compatible(manifest, 28))
        assertFalse(UpdatePolicy.compatible(manifest.copy(minSdk = 34), 33))
    }

    @Test
    fun githubResponseThenManifest() {
        val release = UpdatePolicy.parseRelease(
            """{"tag_name":"v2.28.0","name":"GymFlemme 2.28.0","assets":[
                {"name":"gymflemme-2.28.0.apk","browser_download_url":"https://x/a.apk","size":1},
                {"name":"update.json","browser_download_url":"https://x/update.json"}]}""",
        )!!
        assertEquals("v2.28.0", release.tagName)
        assertEquals("https://x/update.json", UpdatePolicy.manifestUrl(release))
        assertNull(UpdatePolicy.manifestUrl(release.copy(assets = release.assets.take(1))))
        assertNull(UpdatePolicy.parseRelease("<html>"))

        val text = """{"versionCode":60,"versionName":"2.28.0","apkUrl":"${manifest.apkUrl}","sha256":"${"A".repeat(64)}","minSdk":28,"notes":"Nouveautés","extra":1}"""
        assertEquals(manifest.copy(sha256 = "A".repeat(64)), UpdatePolicy.parseManifest(text))
        // Refused: plain http, a checksum of the wrong length, no version.
        assertNull(UpdatePolicy.parseManifest(text.replace("https://", "http://")))
        assertNull(UpdatePolicy.parseManifest(text.replace("A".repeat(64), "abc")))
        assertNull(UpdatePolicy.parseManifest(text.replace("\"versionCode\":60", "\"versionCode\":0")))
        assertNull(UpdatePolicy.parseManifest("not json"))
    }

    @Test
    fun sha256OfTheDownloadedFile() {
        val apk = tmp.newFile("a.apk").apply { writeText("abc") }
        // SHA-256("abc"), the FIPS 180-2 test vector.
        val abc = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        assertEquals(abc, UpdatePolicy.sha256(apk))
        assertTrue(UpdatePolicy.checksumMatches(apk, abc.uppercase()))
        apk.appendText("!") // altered on the way
        assertFalse(UpdatePolicy.checksumMatches(apk, abc))
    }
}

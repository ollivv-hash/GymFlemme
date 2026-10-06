package fr.olivier.forme.feature.ai.data

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** An installed app able to receive a shared text. */
data class ShareTarget(val packageName: String, val label: String, val isAi: Boolean)

/**
 * How the analysis is handed to the AI app. Tested with Claude (lot 0): the file alone works and its lead text is
 * dropped when a file is attached; the text alone works too.
 */
enum class ShareMode {
    FILE_AND_TEXT,
    TEXT,
    FILE,
}

/**
 * Hands a Markdown document to an AI app through the Android share sheet: the file (from the cache, through the
 * FileProvider) and/or its text, with a short lead sentence. Nothing is sent by Forme itself.
 */
@Singleton
class AiShare @Inject constructor(@ApplicationContext private val context: Context) {

    /** Apps accepting a shared text, known AI apps first. */
    fun targets(): List<ShareTarget> {
        val pm = context.packageManager
        val query = Intent(Intent.ACTION_SEND).setType("text/plain")
        return pm.queryIntentActivities(query, PackageManager.MATCH_DEFAULT_ONLY)
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .filter { (pkg, _) -> pkg != context.packageName }
            .distinctBy { it.first }
            .map { (pkg, label) -> ShareTarget(pkg, KNOWN_AI[pkg] ?: label, pkg in KNOWN_AI) }
            .sortedWith(compareBy({ !it.isAi }, { it.label.lowercase() }))
    }

    fun label(packageName: String): String? = runCatching {
        val pm = context.packageManager
        KNOWN_AI[packageName] ?: pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrNull()

    /** Writes [markdown] to the share cache and returns its content URI. */
    fun writeFile(fileName: String, markdown: String): Uri {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, fileName).apply { writeText(markdown) }
        return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    fun intent(mode: ShareMode, fileName: String, markdown: String, lead: String): Intent {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
        if (mode != ShareMode.TEXT) {
            val uri = writeFile(fileName, markdown)
            send.putExtra(Intent.EXTRA_STREAM, uri)
            send.clipData = ClipData.newUri(context.contentResolver, fileName, uri)
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        when (mode) {
            ShareMode.FILE_AND_TEXT -> send.putExtra(Intent.EXTRA_TEXT, lead)
            ShareMode.TEXT -> send.putExtra(Intent.EXTRA_TEXT, "$lead\n\n$markdown")
            ShareMode.FILE -> Unit
        }
        send.putExtra(Intent.EXTRA_SUBJECT, fileName.removeSuffix(".md"))
        return send
    }

    /**
     * Opens [packageName] with the shared content, or the system chooser when it is null or cannot receive it.
     * The text is also copied to the clipboard, to paste it anywhere if the app ignores part of the share.
     */
    fun share(packageName: String?, mode: ShareMode, fileName: String, markdown: String, lead: String) {
        copy("$lead\n\n$markdown")
        val send = intent(mode, fileName, markdown, lead)
        val direct = packageName?.let { Intent(send).setPackage(it).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        try {
            if (direct != null) {
                context.startActivity(direct)
                return
            }
        } catch (_: ActivityNotFoundException) {
            // Uninstalled or no longer accepting shares: fall back to the chooser.
        }
        context.startActivity(Intent.createChooser(send, "Analyse IA").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun copy(text: String) {
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Analyse IA", text))
    }

    companion object {
        /** Known AI assistants, listed first with a clean name. */
        val KNOWN_AI = mapOf(
            "com.anthropic.claude" to "Claude",
            "com.openai.chatgpt" to "ChatGPT",
            "com.google.android.apps.bard" to "Gemini",
            "ai.perplexity.app.android" to "Perplexity",
            "com.microsoft.copilot" to "Copilot",
            "ai.mistral.chat" to "Le Chat (Mistral)",
        )
    }
}

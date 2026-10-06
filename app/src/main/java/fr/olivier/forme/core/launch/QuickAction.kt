package fr.olivier.forme.core.launch

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import fr.olivier.forme.MainActivity
import fr.olivier.forme.R

/**
 * Actions opened from outside the app: launcher shortcuts (long press on the icon) and the home-screen widget.
 * They reach [MainActivity] as an intent action, then the navigation performs them.
 */
enum class QuickAction(val action: String, val shortLabel: String, val longLabel: String, val icon: Int) {
    START_SESSION("fr.olivier.forme.action.START_SESSION", "Séance", "Démarrer la séance", R.drawable.ic_shortcut_session),
    ADD_MEAL("fr.olivier.forme.action.ADD_MEAL", "Repas", "Ajouter un repas", R.drawable.ic_shortcut_meal),
    SCAN("fr.olivier.forme.action.SCAN", "Scanner", "Scanner un code-barres", R.drawable.ic_shortcut_scan),
    ;

    fun intent(context: Context): Intent =
        Intent(context, MainActivity::class.java).setAction(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    companion object {
        fun from(intent: Intent?): QuickAction? = entries.firstOrNull { it.action == intent?.action }

        /**
         * Publishes the launcher shortcuts. Dynamic rather than static (res/xml/shortcuts.xml): a static shortcut
         * names the package in plain text, which would break the debug build (".debug" suffix).
         */
        fun publishShortcuts(context: Context) {
            val shortcuts = entries.map { a ->
                ShortcutInfoCompat.Builder(context, a.name)
                    .setShortLabel(a.shortLabel)
                    .setLongLabel(a.longLabel)
                    .setIcon(IconCompat.createWithResource(context, a.icon))
                    .setIntent(a.intent(context))
                    .build()
            }
            ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
        }
    }
}

package app.statusprep.storage

import android.content.Context
import app.statusprep.core.EncodingConfig
import java.io.File

/** Emplacements des fichiers temporaires dans le cache privé de l'application. */
object FileStore {
    /** Sorties en cours d'écriture : jamais partagées, supprimées en cas d'annulation ou d'erreur. */
    fun workDir(context: Context): File = File(context.cacheDir, "work").apply { mkdirs() }

    /** Résultats terminés (et copies d'originaux) : exposés via FileProvider pour le partage. */
    fun outputsDir(context: Context): File = File(context.cacheDir, "outputs").apply { mkdirs() }

    /**
     * Appelé au démarrage du processus, donc aucun traitement n'est en cours : tout reste de
     * `work/` est un fichier partiel. Les résultats ne sont supprimés qu'après une durée de
     * rétention, jamais juste après un partage (l'application destinataire peut encore lire).
     */
    fun cleanStale(context: Context, now: Long = System.currentTimeMillis()) {
        File(context.cacheDir, "work").listFiles()?.forEach { it.deleteRecursively() }
        File(context.cacheDir, "outputs").listFiles()?.forEach { f ->
            if (now - f.lastModified() > EncodingConfig.STALE_FILES_MAX_AGE_MS) f.delete()
        }
    }
}

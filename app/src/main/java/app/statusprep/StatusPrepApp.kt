package app.statusprep

import android.app.Application
import app.statusprep.storage.FileStore
import kotlin.concurrent.thread

class StatusPrepApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Nettoyage hors thread principal : le processus démarre, aucun traitement n'est en cours.
        thread(name = "cleanup") { runCatching { FileStore.cleanStale(this) } }
    }
}

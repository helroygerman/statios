package app.statusprep.storage

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Enregistre un fichier dans Movies/StatusPrep via MediaStore (aucune permission de stockage requise). */
object GallerySaver {

    suspend fun save(context: Context, file: File): Uri = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val name = "StatusPrep_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".mp4"
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/StatusPrep")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val target = resolver.insert(collection, values) ?: throw IOException("MediaStore a refusé la création du fichier.")
        try {
            resolver.openOutputStream(target)?.use { out ->
                file.inputStream().use { it.copyTo(out) }
            } ?: throw IOException("Impossible d'ouvrir le fichier de destination.")
            resolver.update(
                target,
                ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) },
                null, null,
            )
            target
        } catch (e: Exception) {
            runCatching { resolver.delete(target, null, null) }
            throw IOException(
                if (e.message?.contains("ENOSPC") == true) "Stockage insuffisant pour enregistrer la vidéo."
                else "Échec de l'enregistrement : ${e.message ?: "erreur inconnue"}",
                e,
            )
        }
    }
}

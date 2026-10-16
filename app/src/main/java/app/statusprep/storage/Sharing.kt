package app.statusprep.storage

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class ShareTarget(val packageName: String?) {
    WHATSAPP("com.whatsapp"),
    WHATSAPP_BUSINESS("com.whatsapp.w4b"),
    OTHER(null),
}

/**
 * Partage via une URI FileProvider avec autorisation temporaire de lecture. L'utilisateur choisit
 * lui-même « Mon statut » dans WhatsApp : aucune API privée, aucun accès aux dossiers de WhatsApp.
 */
object Sharing {

    fun installedWhatsAppTargets(context: Context): List<ShareTarget> =
        listOf(ShareTarget.WHATSAPP, ShareTarget.WHATSAPP_BUSINESS).filter { isInstalled(context, it.packageName!!) }

    fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    /** Retourne false si l'application ciblée est introuvable (le repli standard est alors tenté). */
    fun share(context: Context, file: File, mime: String, target: ShareTarget): Boolean {
        val uri = uriFor(context, file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val pkg = target.packageName
        return try {
            if (pkg != null) {
                context.startActivity(send.setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } else {
                context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    /**
     * Copie l'original dans le cache pour le partager via FileProvider : une URI de sélecteur
     * système n'est pas forcément transmissible à une autre application.
     */
    suspend fun copyOriginalForSharing(context: Context, source: Uri): Pair<File, String> = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val mime = resolver.getType(source)?.takeIf { it.startsWith("video/") } ?: "video/*"
        val ext = when (mime) {
            "video/quicktime" -> "mov"
            "video/webm" -> "webm"
            "video/3gpp" -> "3gp"
            else -> "mp4"
        }
        val dest = File(FileStore.outputsDir(context), "original_${System.currentTimeMillis()}.$ext")
        try {
            resolver.openInputStream(source)?.use { input ->
                dest.outputStream().use { input.copyTo(it) }
            } ?: throw IOException("Impossible de relire la vidéo d'origine.")
        } catch (e: Exception) {
            dest.delete()
            throw IOException(
                if (e.message?.contains("ENOSPC") == true) "Stockage insuffisant pour préparer l'original."
                else "Impossible de préparer l'original : ${e.message ?: "erreur inconnue"}",
                e,
            )
        }
        dest to mime
    }

    @Suppress("DEPRECATION")
    private fun isInstalled(context: Context, pkg: String): Boolean = try {
        context.packageManager.getPackageInfo(pkg, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }
}

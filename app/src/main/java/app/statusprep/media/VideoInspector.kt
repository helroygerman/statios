package app.statusprep.media

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import app.statusprep.core.displaySize
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Lit les métadonnées d'une vidéo via MediaMetadataRetriever / MediaExtractor (hors thread principal). */
object VideoInspector {

    suspend fun inspect(context: Context, uri: Uri): VideoInfo = withContext(Dispatchers.IO) {
        inspectBlocking(context.applicationContext, uri) { queryNameAndSize(context.applicationContext, uri) }
    }

    suspend fun inspect(context: Context, file: File): VideoInfo = withContext(Dispatchers.IO) {
        inspectBlocking(context.applicationContext, Uri.fromFile(file)) { file.name to file.length() }
    }

    private fun inspectBlocking(
        context: Context,
        uri: Uri,
        nameAndSize: () -> Pair<String?, Long?>,
    ): VideoInfo {
        val (name, size) = nameAndSize()

        var codedW = 0
        var codedH = 0
        var rotation = 0
        var durationMs: Long? = null
        var overallBitrate: Long? = null
        var frameCount: Long? = null
        var hasVideoMeta = false

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            hasVideoMeta = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes"
            codedW = retriever.int(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH) ?: 0
            codedH = retriever.int(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT) ?: 0
            rotation = retriever.int(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION) ?: 0
            durationMs = retriever.long(MediaMetadataRetriever.METADATA_KEY_DURATION)
            overallBitrate = retriever.long(MediaMetadataRetriever.METADATA_KEY_BITRATE)
            frameCount = retriever.long(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)
        } catch (e: RuntimeException) {
            throw UnsupportedMediaException("Impossible de lire cette vidéo.", e)
        } finally {
            runCatching { retriever.release() }
        }

        var videoMime: String? = null
        var audioMime: String? = null
        var videoBitrate: Long? = null
        var audioBitrate: Long? = null
        var frameRate: Float? = null
        var hasAudio = false
        var hasVideoTrack = false
        var isHdr = false

        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                when {
                    mime.startsWith("video/") && !hasVideoTrack -> {
                        hasVideoTrack = true
                        videoMime = mime
                        if (codedW == 0) codedW = f.intOrNull(MediaFormat.KEY_WIDTH) ?: 0
                        if (codedH == 0) codedH = f.intOrNull(MediaFormat.KEY_HEIGHT) ?: 0
                        if (rotation == 0) rotation = f.intOrNull(MediaFormat.KEY_ROTATION) ?: 0
                        videoBitrate = f.intOrNull(MediaFormat.KEY_BIT_RATE)?.toLong()?.takeIf { it > 0 }
                        frameRate = f.floatOrNull(MediaFormat.KEY_FRAME_RATE)?.takeIf { it > 0f }
                        val transfer = f.intOrNull(MediaFormat.KEY_COLOR_TRANSFER)
                        isHdr = transfer == MediaFormat.COLOR_TRANSFER_ST2084 ||
                            transfer == MediaFormat.COLOR_TRANSFER_HLG ||
                            mime.equals("video/dolby-vision", ignoreCase = true)
                    }
                    mime.startsWith("audio/") && !hasAudio -> {
                        hasAudio = true
                        audioMime = mime
                        audioBitrate = f.intOrNull(MediaFormat.KEY_BIT_RATE)?.toLong()?.takeIf { it > 0 }
                    }
                }
            }
        } catch (e: Exception) {
            if (!hasVideoMeta) throw UnsupportedMediaException("Format de fichier non pris en charge.", e)
        } finally {
            runCatching { extractor.release() }
        }

        if (!hasVideoTrack && !hasVideoMeta) {
            throw UnsupportedMediaException("Ce fichier ne contient pas de piste vidéo lisible.")
        }
        if (codedW <= 0 || codedH <= 0) {
            throw UnsupportedMediaException("Dimensions de la vidéo introuvables.")
        }

        // Cadence : valeur déclarée, sinon nombre d'images / durée.
        if (frameRate == null && frameCount != null && frameCount > 0 && durationMs != null && durationMs > 0) {
            frameRate = frameCount * 1000f / durationMs
        }

        // Débit vidéo : déclaré, sinon débit global moins l'audio (approximation signalée).
        var approx = false
        if (videoBitrate == null) {
            val overall = overallBitrate ?: if (size != null && durationMs != null && durationMs > 0) {
                size * 8_000L / durationMs
            } else null
            if (overall != null) {
                videoBitrate = (overall - (audioBitrate ?: 0L)).takeIf { it > 0 } ?: overall
                approx = true
            }
        }

        val (dw, dh) = displaySize(codedW, codedH, rotation)
        return VideoInfo(
            displayName = name,
            sizeBytes = size,
            durationMs = durationMs,
            width = dw,
            height = dh,
            videoMime = videoMime,
            frameRate = frameRate,
            videoBitrate = videoBitrate,
            bitrateIsApproximate = approx,
            hasAudio = hasAudio,
            audioMime = audioMime,
            audioBitrate = audioBitrate,
            isHdr = isHdr,
        )
    }

    private fun queryNameAndSize(context: Context, uri: Uri): Pair<String?, Long?> {
        var name: String? = null
        var size: Long? = null
        if (uri.scheme == "content") {
            runCatching {
                context.contentResolver.query(
                    uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null,
                )?.use { c ->
                    if (c.moveToFirst()) {
                        val n = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val s = c.getColumnIndex(OpenableColumns.SIZE)
                        if (n >= 0 && !c.isNull(n)) name = c.getString(n)
                        if (s >= 0 && !c.isNull(s)) size = c.getLong(s)
                    }
                }
            }
        }
        if (size == null) {
            size = runCatching {
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length.takeIf { l -> l >= 0 } }
            }.getOrNull()
        }
        return name to size
    }

    private fun MediaMetadataRetriever.long(key: Int): Long? = extractMetadata(key)?.toLongOrNull()
    private fun MediaMetadataRetriever.int(key: Int): Int? = extractMetadata(key)?.toIntOrNull()

    private fun MediaFormat.intOrNull(key: String): Int? =
        if (containsKey(key)) runCatching { getInteger(key) }.getOrNull() else null

    private fun MediaFormat.floatOrNull(key: String): Float? {
        if (!containsKey(key)) return null
        return runCatching { getFloat(key) }.getOrNull()
            ?: runCatching { getInteger(key).toFloat() }.getOrNull()
    }
}

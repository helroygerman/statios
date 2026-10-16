package app.statusprep.media

import app.statusprep.core.SourceMedia

/** Caractéristiques lues sur un média réel (jamais simulées). Les champs inconnus sont null. */
data class VideoInfo(
    val displayName: String?,
    val sizeBytes: Long?,
    val durationMs: Long?,
    /** Dimensions d'affichage, rotation appliquée. */
    val width: Int,
    val height: Int,
    val videoMime: String?,
    val frameRate: Float?,
    /** Débit vidéo en b/s ; peut être approché (débit global) si le flux ne le déclare pas. */
    val videoBitrate: Long?,
    val bitrateIsApproximate: Boolean,
    val hasAudio: Boolean,
    val audioMime: String?,
    val audioBitrate: Long?,
    val isHdr: Boolean,
) {
    val aspectRatio: Float get() = if (height > 0) width.toFloat() / height else 16f / 9f

    fun toSourceMedia() = SourceMedia(
        width = width,
        height = height,
        durationMs = durationMs,
        frameRate = frameRate,
        videoBitrate = videoBitrate,
        videoMime = videoMime,
        hasAudio = hasAudio,
    )
}

class UnsupportedMediaException(message: String, cause: Throwable? = null) : Exception(message, cause)

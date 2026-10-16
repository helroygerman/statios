package app.statusprep.core

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Caractéristiques de la source utiles au calcul. Dimensions APRÈS application de la rotation. */
data class SourceMedia(
    val width: Int,
    val height: Int,
    val durationMs: Long?,
    val frameRate: Float?,
    /** Débit vidéo connu (ou approché) en b/s. */
    val videoBitrate: Long?,
    val videoMime: String?,
    val hasAudio: Boolean,
)

data class OutputPlan(
    val width: Int,
    val height: Int,
    /** Vrai si les dimensions de sortie diffèrent de celles de la source (réduction ou arrondi pair). */
    val needsResize: Boolean,
    val videoBitrate: Int,
    /** Null si la source n'a pas de piste audio. */
    val audioBitrate: Int?,
    /** Cadence maximale à appliquer, ou null s'il n'y a rien à limiter. */
    val frameRateCap: Int?,
    /** Estimation indicative de la taille finale, null si la durée est inconnue. */
    val estimatedBytes: Long?,
    /** Vrai si le débit demandé a été abaissé pour ne pas dépasser celui de la source. */
    val bitrateLimitedBySource: Boolean,
)

object OutputPlanner {

    fun plan(source: SourceMedia, spec: ProfileSpec): OutputPlan {
        val (w, h) = fitDimensions(
            source.width, source.height,
            maxLong = spec.resolution.longSide, maxShort = spec.resolution.shortSide,
        )

        val fpsOut = source.frameRate?.coerceAtMost(spec.maxFps.toFloat()) ?: spec.maxFps.toFloat()
        val bppCap = (w.toLong() * h * fpsOut * spec.maxBitsPerPixel).toLong()
        var target = max(min(spec.videoBitrate.toLong(), bppCap), EncodingConfig.MIN_VIDEO_BITRATE.toLong())

        var limited = false
        val srcBitrate = source.videoBitrate
        if (srcBitrate != null && srcBitrate > 0) {
            val sourceCap = (srcBitrate * EncodingConfig.codecEfficiencyFactor(source.videoMime)).toLong()
            if (sourceCap < target) {
                target = sourceCap
                limited = true
            }
        }

        val audio = if (source.hasAudio) spec.audioBitrate else null
        val frameCap = source.frameRate.let { fps ->
            if (fps == null || fps > spec.maxFps + 0.5f) spec.maxFps else null
        }
        val estimate = source.durationMs?.let { ms ->
            ((target + (audio ?: 0)) / 8.0 * (ms / 1000.0) * CONTAINER_OVERHEAD).toLong()
        }

        return OutputPlan(
            width = w,
            height = h,
            needsResize = w != source.width || h != source.height,
            videoBitrate = target.toInt(),
            audioBitrate = audio,
            frameRateCap = frameCap,
            estimatedBytes = estimate,
            bitrateLimitedBySource = limited,
        )
    }

    /**
     * Ajuste (w, h) dans le cadre maxLong x maxShort, sans jamais agrandir, en conservant le
     * rapport d'aspect. Fonctionne pareil en portrait et en paysage. Résultat toujours pair
     * (exigence de l'encodage H.264 4:2:0).
     */
    fun fitDimensions(width: Int, height: Int, maxLong: Int, maxShort: Int): Pair<Int, Int> {
        require(width > 0 && height > 0) { "Dimensions invalides : ${width}x$height" }
        val long = max(width, height).toDouble()
        val short = min(width, height).toDouble()
        val scale = minOf(1.0, maxLong / long, maxShort / short)
        if (scale >= 1.0) return floorEven(width) to floorEven(height)
        val w = min(nearestEven(width * scale), floorEven(width))
        val h = min(nearestEven(height * scale), floorEven(height))
        return w to h
    }

    private const val CONTAINER_OVERHEAD = 1.02

    private fun floorEven(v: Int): Int = max(2, v - (v % 2))
    private fun nearestEven(v: Double): Int = max(2, (v / 2.0).roundToInt() * 2)
}

/** Les métadonnées de rotation 90/270 échangent largeur et hauteur d'affichage. */
fun displaySize(codedWidth: Int, codedHeight: Int, rotationDegrees: Int): Pair<Int, Int> =
    if (((rotationDegrees % 360) + 360) % 360 in setOf(90, 270)) codedHeight to codedWidth
    else codedWidth to codedHeight

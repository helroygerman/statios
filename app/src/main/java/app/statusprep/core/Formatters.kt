package app.statusprep.core

import java.util.Locale

/** Formatage en français, indépendant de la locale du système pour rester testable. */
object Formatters {
    private val FR = Locale.FRANCE

    fun bytes(value: Long?): String {
        if (value == null || value < 0) return "—"
        val kb = 1024.0
        val mb = kb * 1024
        val gb = mb * 1024
        return when {
            value >= gb -> String.format(FR, "%.2f Go", value / gb)
            value >= mb -> String.format(FR, "%.1f Mo", value / mb)
            value >= kb -> String.format(FR, "%.0f Ko", value / kb)
            else -> "$value o"
        }
    }

    fun duration(ms: Long?): String {
        if (ms == null || ms < 0) return "—"
        val total = (ms + 500) / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format(FR, "%d:%02d:%02d", h, m, s) else String.format(FR, "%d:%02d", m, s)
    }

    fun bitrate(bps: Long?): String {
        if (bps == null || bps <= 0) return "—"
        return if (bps >= 1_000_000) String.format(FR, "%.1f Mb/s", bps / 1_000_000.0)
        else String.format(FR, "%.0f kb/s", bps / 1000.0)
    }

    fun frameRate(fps: Float?): String =
        if (fps == null || fps <= 0f) "—" else String.format(FR, "%.0f images/s", fps)

    fun dimensions(width: Int?, height: Int?): String =
        if (width == null || height == null || width <= 0 || height <= 0) "—" else "$width × $height"

    fun videoCodec(mime: String?): String = when (mime?.lowercase()) {
        null -> "—"
        "video/avc" -> "H.264"
        "video/hevc" -> "H.265 (HEVC)"
        "video/x-vnd.on2.vp9" -> "VP9"
        "video/x-vnd.on2.vp8" -> "VP8"
        "video/av01" -> "AV1"
        "video/mp4v-es" -> "MPEG-4"
        "video/3gpp" -> "H.263"
        "video/dolby-vision" -> "Dolby Vision"
        else -> mime.substringAfter('/').uppercase()
    }

    fun audioCodec(mime: String?): String = when (mime?.lowercase()) {
        null -> "—"
        "audio/mp4a-latm" -> "AAC"
        "audio/mpeg" -> "MP3"
        "audio/opus" -> "Opus"
        "audio/vorbis" -> "Vorbis"
        "audio/ac3" -> "AC-3"
        "audio/eac3" -> "E-AC-3"
        "audio/amr-wb", "audio/3gpp" -> "AMR"
        else -> mime.substringAfter('/').uppercase()
    }
}

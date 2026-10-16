package app.statusprep.core

/**
 * Point unique de réglage des profils. Tous les chiffres ci-dessous sont EXPÉRIMENTAUX :
 * ce ne sont pas des réglages officiels WhatsApp. À ajuster après des essais réels.
 */
object EncodingConfig {
    /** Cadence maximale en sortie. Une cadence source plus faible n'est jamais augmentée. */
    const val MAX_FPS = 30

    /** Plancher de débit vidéo (b/s) pour éviter des sorties inutilisables. */
    const val MIN_VIDEO_BITRATE = 400_000

    /**
     * Plafond de bits par pixel et par image : ne sert qu'à éviter de gaspiller du débit sur
     * de très petites vidéos (ex. 480x270 à 5 Mb/s). N'a aucun effet aux résolutions courantes.
     */
    const val MAX_BITS_PER_PIXEL = 0.2

    /** Débit audio AAC cible (b/s). Plage visée : 96 à 128 kb/s. */
    const val AUDIO_BITRATE_BALANCED = 96_000
    const val AUDIO_BITRATE_DETAILED = 128_000
    const val AUDIO_BITRATE_CUSTOM = 128_000

    /** Bornes du profil personnalisé, en Mb/s. */
    const val CUSTOM_MIN_MBPS = 1f
    const val CUSTOM_MAX_MBPS = 8f
    const val CUSTOM_STEP_MBPS = 0.5f
    const val CUSTOM_DEFAULT_MBPS = 3f
    val CUSTOM_DEFAULT_RESOLUTION = Resolution.P720

    /** Marge appliquée à l'estimation de taille lors du contrôle de l'espace disque. */
    const val STORAGE_SAFETY_FACTOR = 1.3
    const val STORAGE_SAFETY_BYTES = 50L * 1024 * 1024

    /** Âge à partir duquel les fichiers temporaires/résultats obsolètes sont supprimés. */
    const val STALE_FILES_MAX_AGE_MS = 3L * 24 * 60 * 60 * 1000

    /**
     * Un codec source plus efficace que H.264 peut avoir un débit plus bas à qualité égale :
     * le plafonnement "pas plus de débit que la source" tient compte de ce facteur.
     */
    fun codecEfficiencyFactor(mime: String?): Double = when (mime?.lowercase()) {
        "video/hevc", "video/x-vnd.on2.vp9" -> 1.5
        "video/av01" -> 2.0
        else -> 1.0
    }

    fun specFor(selection: ProfileSelection): ProfileSpec = when (selection.id) {
        ProfileId.BALANCED -> ProfileSpec(
            resolution = Resolution.P720,
            videoBitrate = 2_500_000,
            audioBitrate = AUDIO_BITRATE_BALANCED,
        )
        ProfileId.DETAILED -> ProfileSpec(
            resolution = Resolution.P1080,
            videoBitrate = 5_000_000,
            audioBitrate = AUDIO_BITRATE_DETAILED,
        )
        ProfileId.CUSTOM -> ProfileSpec(
            resolution = selection.customResolution,
            videoBitrate = (selection.customMbps.coerceIn(CUSTOM_MIN_MBPS, CUSTOM_MAX_MBPS) * 1_000_000).toInt(),
            audioBitrate = AUDIO_BITRATE_CUSTOM,
        )
    }
}

enum class ProfileId { BALANCED, DETAILED, CUSTOM }

/**
 * Résolution "équivalente Np" : la vidéo doit tenir dans un cadre [longSide] x [shortSide],
 * quelle que soit l'orientation (paysage 1280x720 ou portrait 720x1280 pour 720p).
 */
enum class Resolution(val label: String, val shortSide: Int, val longSide: Int) {
    P480("480p", 480, 854),
    P720("720p", 720, 1280),
    P1080("1080p", 1080, 1920),
}

data class ProfileSpec(
    val resolution: Resolution,
    val videoBitrate: Int,
    val audioBitrate: Int,
    val maxFps: Int = EncodingConfig.MAX_FPS,
    val maxBitsPerPixel: Double = EncodingConfig.MAX_BITS_PER_PIXEL,
)

/** Choix courant de l'utilisateur (le profil personnalisé n'utilise que les deux champs custom). */
data class ProfileSelection(
    val id: ProfileId = ProfileId.BALANCED,
    val customResolution: Resolution = EncodingConfig.CUSTOM_DEFAULT_RESOLUTION,
    val customMbps: Float = EncodingConfig.CUSTOM_DEFAULT_MBPS,
)

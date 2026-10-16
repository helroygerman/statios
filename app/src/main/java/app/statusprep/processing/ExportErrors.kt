package app.statusprep.processing

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.ExportException

/** Traduit une erreur Media3 en message français compréhensible. */
@OptIn(UnstableApi::class)
internal fun describeExportError(e: ExportException, sourceIsHdr: Boolean): String {
    if (sourceIsHdr && e.errorCode in HDR_RELATED_CODES) {
        return "Cette vidéo HDR ne peut pas être convertie en SDR sur cet appareil. " +
            "Le traitement est refusé pour éviter des couleurs délavées."
    }
    return when (e.errorCode) {
        ExportException.ERROR_CODE_IO_FILE_NOT_FOUND ->
            "Le fichier source est introuvable. Sélectionnez à nouveau la vidéo."
        ExportException.ERROR_CODE_IO_NO_PERMISSION ->
            "L'accès au fichier source a expiré. Sélectionnez à nouveau la vidéo."
        ExportException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        ExportException.ERROR_CODE_DECODER_INIT_FAILED ->
            "Le format de cette vidéo n'est pas pris en charge par cet appareil."
        ExportException.ERROR_CODE_ENCODING_FORMAT_UNSUPPORTED,
        ExportException.ERROR_CODE_ENCODER_INIT_FAILED ->
            "L'encodeur de cet appareil n'accepte pas ces réglages. Essayez le profil Équilibré ou un débit plus bas."
        ExportException.ERROR_CODE_MUXING_FAILED ->
            "Impossible d'écrire le fichier MP4 (espace disque insuffisant ?)."
        else -> "Le traitement a échoué."
    }
}

@OptIn(UnstableApi::class)
private val HDR_RELATED_CODES = setOf(
    ExportException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
    ExportException.ERROR_CODE_DECODER_INIT_FAILED,
    ExportException.ERROR_CODE_ENCODING_FORMAT_UNSUPPORTED,
    ExportException.ERROR_CODE_ENCODER_INIT_FAILED,
    ExportException.ERROR_CODE_FAILED_RUNTIME_CHECK,
)

package app.statusprep.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import app.statusprep.core.EncodingConfig
import app.statusprep.core.OutputPlan
import app.statusprep.core.OutputPlanner
import app.statusprep.core.ProfileId
import app.statusprep.core.ProfileSelection
import app.statusprep.core.Resolution
import app.statusprep.media.UnsupportedMediaException
import app.statusprep.media.VideoInfo
import app.statusprep.media.VideoInspector
import app.statusprep.processing.JobRequest
import app.statusprep.processing.JobState
import app.statusprep.processing.ProcessingJobs
import app.statusprep.processing.TranscodeService
import app.statusprep.storage.GallerySaver
import app.statusprep.storage.Sharing
import app.statusprep.storage.ShareTarget
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SourceState {
    data object None : SourceState
    data object Loading : SourceState
    data class Ready(val uri: Uri, val info: VideoInfo) : SourceState
    data class Error(val message: String) : SourceState
}

data class ResultUi(
    val request: JobRequest,
    val file: File,
    val output: VideoInfo,
) {
    val outputBytes: Long get() = file.length()
    val originalBytes: Long? get() = request.source.sizeBytes
    val largerThanOriginal: Boolean get() = originalBytes?.let { outputBytes > it } ?: false
}

/** Fichier prêt à être partagé (résultat d'une préparation asynchrone de l'original). */
data class ShareRequest(val file: File, val mime: String, val target: ShareTarget)

/**
 * Conserve l'état de l'écran (source, profil, résultat). Le traitement lui-même vit dans
 * [ProcessingJobs] / [TranscodeService], donc ni la rotation ni la fermeture de l'activité ne l'interrompent.
 */
class MainViewModel(app: Application, private val saved: SavedStateHandle) : AndroidViewModel(app) {

    private val _source = MutableStateFlow<SourceState>(SourceState.None)
    val source: StateFlow<SourceState> = _source.asStateFlow()

    private val _selection = MutableStateFlow(restoreSelection())
    val selection: StateFlow<ProfileSelection> = _selection.asStateFlow()

    private val _result = MutableStateFlow<ResultUi?>(null)
    val result: StateFlow<ResultUi?> = _result.asStateFlow()

    val job: StateFlow<JobState> = ProcessingJobs.state

    private val _savedToGallery = MutableStateFlow(false)
    val savedToGallery: StateFlow<Boolean> = _savedToGallery.asStateFlow()

    /** Opération d'E/S ponctuelle en cours (enregistrement, copie de l'original). */
    private val _busy = MutableStateFlow<Busy?>(null)
    val busy: StateFlow<Busy?> = _busy.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    private val _pendingShare = MutableStateFlow<ShareRequest?>(null)
    val pendingShare: StateFlow<ShareRequest?> = _pendingShare.asStateFlow()

    var notificationPermissionAsked = false

    enum class Busy { SAVING, PREPARING_ORIGINAL }

    init {
        saved.get<String>(KEY_URI)?.let { loadSource(Uri.parse(it)) }
        viewModelScope.launch {
            ProcessingJobs.state.collect { state ->
                if (state is JobState.Succeeded) onSucceeded(state)
            }
        }
    }

    // ---- Source & profil ---------------------------------------------------------------

    fun onVideoPicked(uri: Uri) {
        runCatching {
            getApplication<Application>().contentResolver
                .takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } // Le sélecteur photo ne l'autorise pas toujours : l'accès temporaire suffit pour cette session.
        _result.value = null
        _savedToGallery.value = false
        ProcessingJobs.acknowledge()
        saved[KEY_URI] = uri.toString()
        loadSource(uri)
    }

    private fun loadSource(uri: Uri) {
        _source.value = SourceState.Loading
        viewModelScope.launch {
            _source.value = try {
                SourceState.Ready(uri, VideoInspector.inspect(getApplication(), uri))
            } catch (e: UnsupportedMediaException) {
                SourceState.Error(e.message ?: "Format non pris en charge.")
            } catch (e: Exception) {
                SourceState.Error("Impossible de lire cette vidéo. Elle n'est peut-être plus accessible : sélectionnez-la à nouveau.")
            }
        }
    }

    fun selectProfile(id: ProfileId) = updateSelection { it.copy(id = id) }
    fun setCustomResolution(r: Resolution) = updateSelection { it.copy(customResolution = r) }
    fun setCustomMbps(v: Float) = updateSelection { it.copy(customMbps = v) }

    private fun updateSelection(f: (ProfileSelection) -> ProfileSelection) {
        val next = f(_selection.value)
        _selection.value = next
        saved[KEY_PROFILE] = next.id.name
        saved[KEY_RES] = next.customResolution.name
        saved[KEY_MBPS] = next.customMbps
    }

    private fun restoreSelection() = ProfileSelection(
        id = saved.get<String>(KEY_PROFILE)?.let { runCatching { ProfileId.valueOf(it) }.getOrNull() } ?: ProfileId.BALANCED,
        customResolution = saved.get<String>(KEY_RES)?.let { runCatching { Resolution.valueOf(it) }.getOrNull() }
            ?: EncodingConfig.CUSTOM_DEFAULT_RESOLUTION,
        customMbps = saved.get<Float>(KEY_MBPS) ?: EncodingConfig.CUSTOM_DEFAULT_MBPS,
    )

    fun planFor(info: VideoInfo, selection: ProfileSelection): OutputPlan =
        OutputPlanner.plan(info.toSourceMedia(), EncodingConfig.specFor(selection))

    // ---- Traitement --------------------------------------------------------------------

    fun startProcessing() {
        val ready = _source.value as? SourceState.Ready ?: return
        if (ProcessingJobs.state.value is JobState.Running) return
        ProcessingJobs.pending = JobRequest(ready.uri, ready.info, planFor(ready.info, _selection.value))
        ProcessingJobs.set(JobState.Running(null))
        try {
            TranscodeService.start(getApplication())
        } catch (e: Exception) {
            ProcessingJobs.set(JobState.Failed("Impossible de lancer le service de traitement.", e.message))
        }
    }

    fun cancelProcessing() = TranscodeService.cancel(getApplication())

    fun acknowledgeJob() = ProcessingJobs.acknowledge()

    private suspend fun onSucceeded(state: JobState.Succeeded) {
        val output = try {
            VideoInspector.inspect(getApplication(), state.outputFile)
        } catch (e: Exception) {
            ProcessingJobs.set(JobState.Failed("Le fichier produit est illisible.", e.message))
            return
        }
        _savedToGallery.value = false
        _result.value = ResultUi(state.request, state.outputFile, output)
        ProcessingJobs.acknowledge()
    }

    fun closeResult() {
        _result.value = null
    }

    // ---- Enregistrement & partage ------------------------------------------------------

    fun saveToGallery() {
        val r = _result.value ?: return
        if (_busy.value != null) return
        _busy.value = Busy.SAVING
        viewModelScope.launch {
            try {
                GallerySaver.save(getApplication(), r.file)
                _savedToGallery.value = true
            } catch (e: Exception) {
                _notice.value = e.message ?: "Échec de l'enregistrement."
            } finally {
                _busy.value = null
            }
        }
    }

    fun shareOriginal(target: ShareTarget) {
        val r = _result.value ?: return
        if (_busy.value != null) return
        _busy.value = Busy.PREPARING_ORIGINAL
        viewModelScope.launch {
            try {
                val (file, mime) = Sharing.copyOriginalForSharing(getApplication(), r.request.sourceUri)
                _pendingShare.value = ShareRequest(file, mime, target)
            } catch (e: Exception) {
                _notice.value = e.message ?: "Impossible de préparer l'original."
            } finally {
                _busy.value = null
            }
        }
    }

    fun consumePendingShare() {
        _pendingShare.value = null
    }

    fun showNotice(message: String) {
        _notice.value = message
    }

    fun consumeNotice() {
        _notice.value = null
    }

    private companion object {
        const val KEY_URI = "uri"
        const val KEY_PROFILE = "profile"
        const val KEY_RES = "res"
        const val KEY_MBPS = "mbps"
    }
}

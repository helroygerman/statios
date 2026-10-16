package app.statusprep.processing

import android.net.Uri
import app.statusprep.core.OutputPlan
import app.statusprep.media.VideoInfo
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class JobRequest(
    val sourceUri: Uri,
    val source: VideoInfo,
    val plan: OutputPlan,
)

sealed interface JobState {
    data object Idle : JobState

    /** [progress] entre 0 et 1, ou null quand l'avancement réel n'est pas disponible. */
    data class Running(val progress: Float?) : JobState

    data class Succeeded(val request: JobRequest, val outputFile: File) : JobState
    data class Failed(val message: String, val detail: String? = null) : JobState
    data object Cancelled : JobState
}

/**
 * État du traitement, au niveau du processus : il survit aux rotations et à la destruction de
 * l'activité, tant que le service de premier plan maintient le processus en vie.
 */
object ProcessingJobs {
    private val _state = MutableStateFlow<JobState>(JobState.Idle)
    val state: StateFlow<JobState> = _state.asStateFlow()

    @Volatile
    var pending: JobRequest? = null

    fun set(state: JobState) {
        _state.value = state
    }

    /** Remet l'état à zéro (résultat consommé, erreur acquittée). Ne touche pas un traitement en cours. */
    fun acknowledge() {
        if (_state.value !is JobState.Running) _state.value = JobState.Idle
    }
}

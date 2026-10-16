package app.statusprep.processing

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.AudioEncoderSettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import app.statusprep.MainActivity
import app.statusprep.R
import app.statusprep.core.EncodingConfig
import app.statusprep.storage.FileStore
import java.io.File

/**
 * Service de premier plan qui exécute le transcodage Media3.
 *
 * Pourquoi un service de premier plan : sans lui, Android peut tuer le processus dès que
 * l'utilisateur quitte l'application. Limites : le système peut tout de même arrêter le service
 * (Android 15 impose une durée maximale cumulée aux services de type `mediaProcessing`, voir
 * [onTimeout]), l'utilisateur peut le forcer à s'arrêter, et un redémarrage du téléphone annule
 * le traitement. Dans ces cas le fichier partiel est supprimé et l'erreur est affichée.
 *
 * Le Transformer travaille sur ses propres threads ; ce service ne fait sur le thread principal
 * que démarrer/arrêter la tâche et lire sa progression.
 */
@OptIn(UnstableApi::class)
class TranscodeService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val progressHolder = ProgressHolder()

    private var transformer: Transformer? = null
    private var request: JobRequest? = null
    private var workFile: File? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var running = false

    private val ticker = object : Runnable {
        override fun run() {
            publishProgress()
            handler.postDelayed(this, PROGRESS_INTERVAL_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                enterForeground()
                if (!running) startJob()
            }
            ACTION_CANCEL -> cancelJob()
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        failJob("Android a arrêté le traitement en arrière-plan (durée maximale atteinte). Relancez-le en gardant l'application ouverte.")
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        if (running) {
            // Arrêt imposé par le système : ne laisser ni tâche ni fichier partiel.
            transformer?.cancel()
            workFile?.delete()
            running = false
            ProcessingJobs.set(JobState.Failed("Le traitement a été interrompu par le système."))
        }
        releaseWakeLock()
        super.onDestroy()
    }

    // ---- Cycle de vie du traitement -----------------------------------------------------

    private fun startJob() {
        val req = ProcessingJobs.pending
        if (req == null) {
            ProcessingJobs.set(JobState.Failed("Aucune demande de traitement."))
            finish()
            return
        }
        request = req
        running = true
        ProcessingJobs.set(JobState.Running(null))

        val dir = FileStore.workDir(this)
        val needed = ((req.plan.estimatedBytes ?: req.source.sizeBytes ?: 0L) * EncodingConfig.STORAGE_SAFETY_FACTOR).toLong() +
            EncodingConfig.STORAGE_SAFETY_BYTES
        if (dir.usableSpace < needed) {
            failJob("Stockage insuffisant : environ ${needed / (1024 * 1024)} Mo sont nécessaires, libérez de l'espace puis réessayez.")
            return
        }

        acquireWakeLock()
        val hdrMode = if (req.source.isHdr) Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_MEDIACODEC else null
        runTransformer(req, File(dir, "job_${System.currentTimeMillis()}.mp4"), hdrMode)
        handler.postDelayed(ticker, PROGRESS_INTERVAL_MS)
    }

    private fun runTransformer(req: JobRequest, out: File, hdrMode: Int?) {
        out.delete()
        workFile = out
        val plan = req.plan

        val encoderFactory = DefaultEncoderFactory.Builder(this)
            .setRequestedVideoEncoderSettings(
                VideoEncoderSettings.Builder().setBitrate(plan.videoBitrate).build(),
            )
            .apply {
                plan.audioBitrate?.let {
                    setRequestedAudioEncoderSettings(AudioEncoderSettings.Builder().setBitrate(it).build())
                }
            }
            // Si l'encodeur matériel refuse un réglage, Media3 choisit la valeur la plus proche
            // au lieu d'échouer : le résultat réel est mesuré et affiché dans l'écran résultat.
            .setEnableFallback(true)
            .build()

        val listener = object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                onTranscodeCompleted(out)
            }

            override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                onTranscodeError(req, out, hdrMode, exportException)
            }
        }

        val t = Transformer.Builder(this)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .setEncoderFactory(encoderFactory)
            .addListener(listener)
            .build()
        transformer = t

        // Presentation en "scale to fit" avec des dimensions de même proportion que la source :
        // aucun recadrage, étirement ou bande ajoutée. Elle n'est ajoutée que pour réduire.
        val videoEffects = if (plan.needsResize) {
            listOf(Presentation.createForWidthAndHeight(plan.width, plan.height, Presentation.LAYOUT_SCALE_TO_FIT))
        } else {
            emptyList()
        }
        val edited = EditedMediaItem.Builder(MediaItem.fromUri(req.sourceUri))
            .setEffects(Effects(emptyList<AudioProcessor>(), videoEffects))
            .apply { plan.frameRateCap?.let { setFrameRate(it) } }
            .build()
        val composition = Composition.Builder(EditedMediaItemSequence.Builder(edited).build())
            .apply { hdrMode?.let { setHdrMode(it) } }
            .build()

        try {
            t.start(composition, out.absolutePath)
        } catch (e: Exception) {
            failJob("Impossible de démarrer le traitement.", e.message)
        }
    }

    private fun onTranscodeCompleted(out: File) {
        val req = request ?: return
        handler.removeCallbacks(ticker)
        if (!out.exists() || out.length() == 0L) {
            failJob("Le fichier produit est vide.")
            return
        }
        val dest = File(FileStore.outputsDir(this), "StatusPrep_${System.currentTimeMillis()}.mp4")
        if (!out.renameTo(dest)) {
            out.delete()
            failJob("Impossible d'enregistrer le résultat.")
            return
        }
        running = false
        ProcessingJobs.set(JobState.Succeeded(req, dest))
        finish()
    }

    private fun onTranscodeError(req: JobRequest, out: File, hdrMode: Int?, e: ExportException) {
        // Source HDR : un seul repli de MediaCodec vers OpenGL pour la conversion SDR ; sinon erreur explicite.
        if (hdrMode == Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_MEDIACODEC) {
            runTransformer(req, out, Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
            return
        }
        failJob(describeExportError(e, req.source.isHdr), "${e.errorCodeName} : ${e.message}")
    }

    private fun cancelJob() {
        if (!running) {
            finish()
            return
        }
        transformer?.cancel()
        workFile?.delete()
        running = false
        handler.removeCallbacks(ticker)
        ProcessingJobs.set(JobState.Cancelled)
        finish()
    }

    private fun failJob(message: String, detail: String? = null) {
        transformer?.cancel()
        workFile?.delete()
        running = false
        handler.removeCallbacks(ticker)
        ProcessingJobs.set(JobState.Failed(message, detail))
        finish()
    }

    private fun finish() {
        releaseWakeLock()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun publishProgress() {
        val t = transformer ?: return
        val state = t.getProgress(progressHolder)
        val progress = if (state == Transformer.PROGRESS_STATE_AVAILABLE) progressHolder.progress / 100f else null
        ProcessingJobs.set(JobState.Running(progress))
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(progress))
    }

    // ---- Notification & premier plan ----------------------------------------------------

    private fun enterForeground() {
        val channel = NotificationChannel(
            CHANNEL_ID, getString(R.string.notif_channel_name), NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        val type = if (Build.VERSION.SDK_INT >= 35) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
        } else {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(null), type)
    }

    private fun buildNotification(progress: Float?): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val cancel = PendingIntent.getService(
            this, 1,
            Intent(this, TranscodeService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(
                if (progress != null) getString(R.string.notif_progress, (progress * 100).toInt())
                else getString(R.string.notif_indeterminate),
            )
            .setProgress(100, ((progress ?: 0f) * 100).toInt(), progress == null)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(0, getString(R.string.cancel), cancel)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    // ---- Maintien du CPU ----------------------------------------------------------------

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "StatusPrep:transcode").apply {
            acquire(WAKE_LOCK_TIMEOUT_MS)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    companion object {
        const val ACTION_START = "app.statusprep.action.START"
        const val ACTION_CANCEL = "app.statusprep.action.CANCEL"
        private const val CHANNEL_ID = "processing"
        private const val NOTIFICATION_ID = 1
        private const val PROGRESS_INTERVAL_MS = 500L
        private const val WAKE_LOCK_TIMEOUT_MS = 2L * 60 * 60 * 1000

        fun start(context: Context) {
            context.startForegroundService(
                Intent(context, TranscodeService::class.java).setAction(ACTION_START),
            )
        }

        fun cancel(context: Context) {
            context.startService(Intent(context, TranscodeService::class.java).setAction(ACTION_CANCEL))
        }
    }
}

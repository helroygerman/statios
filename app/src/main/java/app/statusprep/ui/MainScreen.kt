@file:OptIn(ExperimentalMaterial3Api::class)

package app.statusprep.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.statusprep.R
import app.statusprep.core.EncodingConfig
import app.statusprep.core.Formatters
import app.statusprep.core.OutputPlan
import app.statusprep.core.ProfileId
import app.statusprep.core.ProfileSelection
import app.statusprep.core.Resolution
import app.statusprep.media.VideoInfo
import app.statusprep.processing.JobState

private enum class MainStage { Empty, Loading, Error, Editor, Processing }

@Composable
fun MainScreen(
    vm: MainViewModel,
    source: SourceState,
    selection: ProfileSelection,
    job: JobState,
    snackbar: SnackbarHostState,
) {
    val context = LocalContext.current
    val ready = source as? SourceState.Ready
    val running = job is JobState.Running

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.onVideoPicked(uri)
    }
    val pickVideo = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) }

    // La notification du service de premier plan s'affiche mieux avec l'autorisation, mais le
    // traitement fonctionne aussi sans : on la demande une seule fois puis on lance dans tous les cas.
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.startProcessing()
    }
    val onStart = {
        val needsAsk = Build.VERSION.SDK_INT >= 33 && !vm.notificationPermissionAsked &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsAsk) {
            vm.notificationPermissionAsked = true
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            vm.startProcessing()
        }
    }

    val stage = when {
        source is SourceState.Loading -> MainStage.Loading
        source is SourceState.Error -> MainStage.Error
        ready == null -> MainStage.Empty
        running -> MainStage.Processing
        else -> MainStage.Editor
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge) },
                actions = {
                    if (ready != null) {
                        TextButton(onClick = pickVideo, enabled = !running) {
                            Text(stringResource(R.string.change_video), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                ),
            )
        },
        bottomBar = {
            if (ready != null) {
                BottomActionBar {
                    if (running) {
                        OutlineButton(stringResource(R.string.processing_cancel), vm::cancelProcessing, Modifier.fillMaxWidth())
                    } else {
                        val plan = vm.planFor(ready.info, selection)
                        PrimaryButton(
                            stringResource(if (plan.passThrough) R.string.start_processing_copy else R.string.start_processing),
                            onStart,
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Crossfade(targetState = stage, label = "stage") { s ->
                when (s) {
                    MainStage.Empty -> EmptyState(onPick = pickVideo)
                    MainStage.Loading -> LoadingState()
                    MainStage.Error -> ErrorState((source as? SourceState.Error)?.message.orEmpty(), onPick = pickVideo)
                    MainStage.Processing -> ProcessingPanel(job as? JobState.Running)
                    MainStage.Editor -> if (ready != null) EditorContent(vm, ready, selection, job)
                }
            }
        }
    }
}

@Composable
private fun EmptyState(onPick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = ScreenPadding).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.empty_title), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.empty_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))
        PrimaryButton(stringResource(R.string.pick_video), onPick)
    }
}

@Composable
private fun LoadingState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Text(stringResource(R.string.loading_video), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ErrorState(message: String, onPick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Banner(stringResource(R.string.source_error_title), message, BannerKind.Error)
        PrimaryButton(stringResource(R.string.pick_video), onPick)
    }
}

/** Traitement : un seul élément central, lisible d'un coup d'œil. */
@Composable
private fun ProcessingPanel(job: JobState.Running?) {
    val progress = job?.progress
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(176.dp), contentAlignment = Alignment.Center) {
            if (progress != null) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = 12.dp,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                )
                Text("${(progress * 100).toInt()} %", style = MaterialTheme.typography.displaySmall)
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = 12.dp,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                )
            }
        }
        Text(stringResource(R.string.processing_title), style = MaterialTheme.typography.headlineSmall)
        if (progress == null) {
            Text(
                stringResource(R.string.processing_indeterminate),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
        }
        Text(
            stringResource(R.string.processing_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun EditorContent(vm: MainViewModel, ready: SourceState.Ready, selection: ProfileSelection, job: JobState) {
    val info = ready.info
    var showDetails by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ScreenPadding)
            .padding(top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        if (job is JobState.Failed) {
            Banner(
                stringResource(R.string.failed_title),
                job.message + (job.detail?.let { "\n" + stringResource(R.string.failed_detail, it) } ?: ""),
                BannerKind.Error,
            )
            TextButton(onClick = vm::acknowledgeJob) { Text(stringResource(R.string.dismiss)) }
        }
        if (job is JobState.Cancelled) {
            Banner(stringResource(R.string.cancelled_title), stringResource(R.string.cancelled_body), BannerKind.Info)
            TextButton(onClick = vm::acknowledgeJob) { Text(stringResource(R.string.dismiss)) }
        }

        VideoPreview(ready.uri, info.aspectRatio)

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Pill(Formatters.duration(info.durationMs))
                Pill(Formatters.dimensions(info.width, info.height))
                Pill(Formatters.bytes(info.sizeBytes))
                Pill(Formatters.videoCodec(info.videoMime))
                if (info.isHdr) Pill(stringResource(R.string.hdr_badge), emphasized = true)
            }
            TextButton(onClick = { showDetails = !showDetails }) {
                Text(stringResource(if (showDetails) R.string.details_hide else R.string.details_show))
            }
            AnimatedVisibility(visible = showDetails) {
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        info.displayName?.let {
                            Text(it, style = MaterialTheme.typography.titleMedium, maxLines = 1, modifier = Modifier.padding(vertical = 8.dp))
                            RowDivider()
                        }
                        SpecRow(stringResource(R.string.spec_duration), Formatters.duration(info.durationMs))
                        SpecRow(stringResource(R.string.spec_size), Formatters.bytes(info.sizeBytes))
                        SpecRow(stringResource(R.string.spec_dimensions), Formatters.dimensions(info.width, info.height))
                        SpecRow(stringResource(R.string.spec_video_codec), Formatters.videoCodec(info.videoMime))
                        SpecRow(stringResource(R.string.spec_video_bitrate), bitrateText(info))
                        SpecRow(stringResource(R.string.spec_fps), Formatters.frameRate(info.frameRate))
                        SpecRow(stringResource(R.string.spec_audio), audioText(info))
                    }
                }
            }
        }

        ProfilePicker(vm, selection)
        OutputCard(info, vm.planFor(info, selection))
    }
}

@Composable
private fun ProfilePicker(vm: MainViewModel, selection: ProfileSelection) {
    val options = listOf(
        ProfileId.BALANCED to R.string.profile_balanced,
        ProfileId.DETAILED to R.string.profile_detailed,
        ProfileId.CUSTOM to R.string.profile_custom,
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(stringResource(R.string.section_profile))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (id, label) ->
                SegmentedButton(
                    selected = selection.id == id,
                    onClick = { vm.selectProfile(id) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    modifier = Modifier.height(56.dp),
                    label = { Text(stringResource(label), style = MaterialTheme.typography.labelLarge, maxLines = 1) },
                )
            }
        }
        Text(
            stringResource(
                when (selection.id) {
                    ProfileId.BALANCED -> R.string.profile_balanced_desc
                    ProfileId.DETAILED -> R.string.profile_detailed_desc
                    ProfileId.CUSTOM -> R.string.profile_custom_desc
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        AnimatedVisibility(visible = selection.id == ProfileId.CUSTOM) {
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.custom_resolution), style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Resolution.entries.forEach { r ->
                            FilterChip(
                                selected = selection.customResolution == r,
                                onClick = { vm.setCustomResolution(r) },
                                label = { Text(r.label) },
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(stringResource(R.string.custom_bitrate), style = MaterialTheme.typography.labelLarge)
                        Text(
                            String.format(java.util.Locale.FRANCE, "%.1f Mb/s", selection.customMbps),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    val steps = ((EncodingConfig.CUSTOM_MAX_MBPS - EncodingConfig.CUSTOM_MIN_MBPS) / EncodingConfig.CUSTOM_STEP_MBPS).toInt() - 1
                    Slider(
                        value = selection.customMbps,
                        onValueChange = { vm.setCustomMbps(it) },
                        valueRange = EncodingConfig.CUSTOM_MIN_MBPS..EncodingConfig.CUSTOM_MAX_MBPS,
                        steps = steps,
                    )
                }
            }
        }
    }
}

/** Ce que l'on obtiendra : poids avant → après en grand, détails en dessous. */
@Composable
private fun OutputCard(info: VideoInfo, plan: OutputPlan) {
    val original = info.sizeBytes
    val estimate = plan.estimatedBytes
    val heavier = !plan.passThrough && original != null && estimate != null && estimate > original

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.plan_title), style = MaterialTheme.typography.labelMedium)
            Text(
                text = when {
                    plan.passThrough -> stringResource(R.string.plan_copy_headline)
                    original != null && estimate != null ->
                        stringResource(R.string.plan_size_from_to, Formatters.bytes(original), Formatters.bytes(estimate))
                    estimate != null -> stringResource(R.string.plan_size_only, Formatters.bytes(estimate))
                    else -> Formatters.dimensions(plan.width, plan.height)
                },
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = if (plan.passThrough) {
                    stringResource(R.string.plan_line_source, Formatters.dimensions(plan.width, plan.height), bitrateText(info))
                } else {
                    stringResource(
                        R.string.plan_line,
                        Formatters.dimensions(plan.width, plan.height),
                        Formatters.bitrate(plan.videoBitrate.toLong()),
                    )
                },
                style = MaterialTheme.typography.bodyLarge,
            )

            Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (plan.passThrough) NoteRow(stringResource(R.string.plan_pass_through))
                if (heavier) NoteRow(stringResource(R.string.plan_heavier), emphasized = true)
                if (plan.bitrateLimitedBySource && !plan.passThrough) NoteRow(stringResource(R.string.plan_limited_by_source))
                if (info.isHdr) NoteRow(stringResource(R.string.plan_hdr), emphasized = true)
            }
            if (!plan.passThrough) {
                Text(
                    stringResource(R.string.plan_disclaimer),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

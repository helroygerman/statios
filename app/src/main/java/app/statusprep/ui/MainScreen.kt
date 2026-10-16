@file:OptIn(ExperimentalMaterial3Api::class)

package app.statusprep.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding

import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.statusprep.R
import app.statusprep.core.EncodingConfig
import app.statusprep.core.Formatters
import app.statusprep.core.ProfileId
import app.statusprep.core.ProfileSelection
import app.statusprep.core.Resolution
import app.statusprep.media.VideoInfo
import app.statusprep.processing.JobState

@Composable
fun MainContent(vm: MainViewModel, source: SourceState, selection: ProfileSelection, job: JobState) {
    val context = LocalContext.current
    val running = job is JobState.Running

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.onVideoPicked(uri)
    }
    val pickVideo = {
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
    }

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

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        when (source) {
            SourceState.None -> EmptyState(onPick = pickVideo)
            SourceState.Loading -> LoadingState()
            is SourceState.Error -> {
                Banner(stringResource(R.string.source_error_title), source.message, BannerKind.Error)
                PrimaryButton(stringResource(R.string.pick_video), pickVideo)
            }
            is SourceState.Ready -> {
                VideoPreview(source.uri, source.info.aspectRatio)
                SourceCard(source.info)

                when (job) {
                    is JobState.Running -> ProcessingCard(job, onCancel = vm::cancelProcessing)
                    else -> {
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
                        ProfileSection(vm, source.info, selection)
                        PrimaryButton(stringResource(R.string.start_processing), onStart)
                    }
                }
                SecondaryButton(stringResource(R.string.change_video), pickVideo, enabled = !running)
            }
        }
    }
}

@Composable
private fun EmptyState(onPick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.empty_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            stringResource(R.string.empty_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PrimaryButton(stringResource(R.string.pick_video), onPick, Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun LoadingState() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator()
        Text(stringResource(R.string.loading_video), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SourceCard(info: VideoInfo) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
    ) {
        Column(Modifier.padding(16.dp)) {
            info.displayName?.let {
                Text(it, style = MaterialTheme.typography.titleMedium, maxLines = 1, modifier = Modifier.padding(bottom = 4.dp))
            }
            SpecRow(stringResource(R.string.spec_duration), Formatters.duration(info.durationMs))
            SpecRow(stringResource(R.string.spec_size), Formatters.bytes(info.sizeBytes))
            SpecRow(stringResource(R.string.spec_dimensions), Formatters.dimensions(info.width, info.height))
            SpecRow(
                stringResource(R.string.spec_video_codec),
                Formatters.videoCodec(info.videoMime) + if (info.isHdr) " · " + stringResource(R.string.hdr_badge) else "",
            )
            SpecRow(stringResource(R.string.spec_video_bitrate), bitrateText(info))
            SpecRow(stringResource(R.string.spec_fps), Formatters.frameRate(info.frameRate))
            SpecRow(stringResource(R.string.spec_audio), audioText(info))
        }
    }
}

@Composable
private fun ProfileSection(vm: MainViewModel, info: VideoInfo, selection: ProfileSelection) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.section_profile))
        Text(
            stringResource(R.string.profile_experimental),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ProfileOption(
                stringResource(R.string.profile_balanced), stringResource(R.string.profile_balanced_desc),
                selection.id == ProfileId.BALANCED,
            ) { vm.selectProfile(ProfileId.BALANCED) }
            ProfileOption(
                stringResource(R.string.profile_detailed), stringResource(R.string.profile_detailed_desc),
                selection.id == ProfileId.DETAILED,
            ) { vm.selectProfile(ProfileId.DETAILED) }
            ProfileOption(
                stringResource(R.string.profile_custom), stringResource(R.string.profile_custom_desc),
                selection.id == ProfileId.CUSTOM,
            ) { vm.selectProfile(ProfileId.CUSTOM) }
        }

        if (selection.id == ProfileId.CUSTOM) {
            Card(shape = RoundedCornerShape(16.dp)) {
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
                    Text(
                        stringResource(R.string.custom_bitrate, mbpsText(selection.customMbps)),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = 8.dp),
                    )
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

        PlanCard(vm, info, selection)
    }
}

@Composable
private fun ProfileOption(title: String, description: String, selected: Boolean, onSelect: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
        modifier = Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun PlanCard(vm: MainViewModel, info: VideoInfo, selection: ProfileSelection) {
    val plan = vm.planFor(info, selection)
    Card(shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionTitle(stringResource(R.string.plan_title))
            Text(
                stringResource(
                    R.string.plan_line,
                    Formatters.dimensions(plan.width, plan.height),
                    Formatters.bitrate(plan.videoBitrate.toLong()),
                ),
                style = MaterialTheme.typography.bodyLarge,
            )
            plan.estimatedBytes?.let {
                Text(stringResource(R.string.plan_estimate, Formatters.bytes(it)), style = MaterialTheme.typography.bodyMedium)
            }
            if (plan.bitrateLimitedBySource) {
                Text(stringResource(R.string.plan_limited_by_source), style = MaterialTheme.typography.bodyMedium)
            }
            Text(stringResource(R.string.plan_no_upscale), style = MaterialTheme.typography.bodyMedium)
            if (info.isHdr) {
                Text(
                    stringResource(R.string.plan_hdr),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Text(
                stringResource(R.string.plan_disclaimer),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProcessingCard(job: JobState.Running, onCancel: () -> Unit) {
    Card(shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle(stringResource(R.string.processing_title))
            val progress = job.progress
            if (progress != null) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.processing_percent, (progress * 100).toInt()), style = MaterialTheme.typography.bodyLarge)
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.processing_indeterminate), style = MaterialTheme.typography.bodyLarge)
            }
            Text(
                stringResource(R.string.processing_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SecondaryButton(stringResource(R.string.processing_cancel), onCancel)
        }
    }
}

@Composable
internal fun bitrateText(info: VideoInfo): String {
    val value = Formatters.bitrate(info.videoBitrate)
    return if (info.bitrateIsApproximate && info.videoBitrate != null) "≈ $value" else value
}

@Composable
internal fun audioText(info: VideoInfo): String {
    if (!info.hasAudio) return stringResource(R.string.audio_none)
    val codec = Formatters.audioCodec(info.audioMime)
    return if (info.audioBitrate != null) "$codec · ${Formatters.bitrate(info.audioBitrate)}" else codec
}

private fun mbpsText(v: Float): String = String.format(java.util.Locale.FRANCE, "%.1f Mb/s", v)

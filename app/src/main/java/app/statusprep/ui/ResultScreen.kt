@file:OptIn(ExperimentalMaterial3Api::class)

package app.statusprep.ui

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.statusprep.R
import app.statusprep.core.Formatters
import app.statusprep.media.VideoInfo
import app.statusprep.storage.ShareTarget
import app.statusprep.storage.Sharing

@Composable
fun ResultScreen(
    vm: MainViewModel,
    result: ResultUi,
    savedToGallery: Boolean,
    busy: MainViewModel.Busy?,
    snackbar: SnackbarHostState,
) {
    val context = LocalContext.current
    val whatsapp = remember { Sharing.installedWhatsAppTargets(context) }
    val previewUri = remember(result.file) { Uri.fromFile(result.file) }

    fun shareResult(target: ShareTarget) {
        if (!Sharing.share(context, result.file, "video/mp4", target)) {
            vm.showNotice(context.getString(R.string.share_app_missing))
            Sharing.share(context, result.file, "video/mp4", ShareTarget.OTHER)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.result_title), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = vm::closeResult) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                ),
            )
        },
        bottomBar = {
            BottomActionBar {
                Text(
                    stringResource(R.string.share_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (whatsapp.isEmpty()) {
                    PrimaryButton(stringResource(R.string.share_generic), { shareResult(ShareTarget.OTHER) })
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (ShareTarget.WHATSAPP in whatsapp) {
                            PrimaryButton(
                                stringResource(R.string.share_whatsapp),
                                { shareResult(ShareTarget.WHATSAPP) },
                                Modifier.weight(1f),
                            )
                        }
                        if (ShareTarget.WHATSAPP_BUSINESS in whatsapp) {
                            val business: @Composable () -> Unit = {
                                if (ShareTarget.WHATSAPP in whatsapp) {
                                    SecondaryButton(
                                        stringResource(R.string.share_business),
                                        { shareResult(ShareTarget.WHATSAPP_BUSINESS) },
                                        Modifier.weight(1f),
                                    )
                                } else {
                                    PrimaryButton(
                                        stringResource(R.string.share_business),
                                        { shareResult(ShareTarget.WHATSAPP_BUSINESS) },
                                        Modifier.weight(1f),
                                    )
                                }
                            }
                            business()
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlineButton(
                        text = when {
                            busy == MainViewModel.Busy.SAVING -> stringResource(R.string.saving)
                            savedToGallery -> stringResource(R.string.saved_gallery)
                            else -> stringResource(R.string.save_gallery)
                        },
                        onClick = vm::saveToGallery,
                        enabled = !savedToGallery && busy == null,
                        modifier = Modifier.weight(1f),
                    )
                    OutlineButton(
                        text = stringResource(R.string.share_other),
                        onClick = { shareResult(ShareTarget.OTHER) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ScreenPadding)
                .padding(top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            VideoPreview(previewUri, result.output.aspectRatio)
            OutcomeCard(result)
            ComparisonCard(result.request.source, result.output, result.outputBytes)
            OriginalCard(
                preparing = busy == MainViewModel.Busy.PREPARING_ORIGINAL,
                enabled = busy == null,
                onShare = { vm.shareOriginal(whatsapp.firstOrNull() ?: ShareTarget.OTHER) },
            )
        }
    }
}

/** Le verdict en un coup d'œil : gain, copie sans perte, ou avertissement si plus lourd. */
@Composable
private fun OutcomeCard(result: ResultUi) {
    val original = result.originalBytes
    val out = result.outputBytes
    val passed = result.request.plan.passThrough
    val larger = result.largerThanOriginal

    val container = if (larger) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer
    val content = if (larger) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer

    val title: String
    val body: String
    when {
        passed -> {
            title = stringResource(R.string.result_passed_title)
            body = stringResource(R.string.result_passed_body)
        }
        larger -> {
            title = stringResource(R.string.result_larger_title)
            body = stringResource(R.string.result_larger_body, Formatters.bytes(out), Formatters.bytes(original))
        }
        original != null && original > 0 -> {
            val saved = original - out
            title = stringResource(R.string.result_smaller_title, (saved * 100 / original).toInt())
            body = stringResource(R.string.result_smaller_body, Formatters.bytes(saved), Formatters.bytes(original), Formatters.bytes(out))
        }
        else -> {
            title = Formatters.bytes(out)
            body = ""
        }
    }

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            if (body.isNotEmpty()) Text(body, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun ComparisonCard(before: VideoInfo, after: VideoInfo, afterBytes: Long) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            SectionTitle(stringResource(R.string.compare_title), Modifier.padding(bottom = 4.dp))
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Spacer(Modifier.weight(0.8f))
                Text(
                    stringResource(R.string.compare_before),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                )
                Text(
                    stringResource(R.string.compare_after),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.End,
                )
            }
            RowDivider()
            CompareRow(stringResource(R.string.spec_dimensions), Formatters.dimensions(before.width, before.height), Formatters.dimensions(after.width, after.height))
            CompareRow(stringResource(R.string.spec_size), Formatters.bytes(before.sizeBytes), Formatters.bytes(afterBytes))
            CompareRow(stringResource(R.string.spec_video_codec), Formatters.videoCodec(before.videoMime), Formatters.videoCodec(after.videoMime))
            CompareRow(stringResource(R.string.spec_video_bitrate), bitrateText(before), bitrateText(after))
            CompareRow(stringResource(R.string.spec_fps), Formatters.frameRate(before.frameRate), Formatters.frameRate(after.frameRate))
            CompareRow(stringResource(R.string.spec_audio), audioText(before), audioText(after))
            Text(
                stringResource(R.string.compare_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun CompareRow(label: String, before: String, after: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Text(label, modifier = Modifier.weight(0.8f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(before, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End)
        Text(after, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
    }
}

@Composable
private fun OriginalCard(preparing: Boolean, enabled: Boolean, onShare: () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle(stringResource(R.string.original_title))
            Text(
                stringResource(R.string.original_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (preparing) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.padding(4.dp), strokeWidth = 3.dp)
                    Text(stringResource(R.string.preparing_original), style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                OutlineButton(stringResource(R.string.original_share), onShare, Modifier.fillMaxWidth(), enabled = enabled)
            }
        }
    }
}

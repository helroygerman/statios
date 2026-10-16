package app.statusprep.ui

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.statusprep.R
import app.statusprep.core.Formatters
import app.statusprep.media.VideoInfo
import app.statusprep.storage.Sharing
import app.statusprep.storage.ShareTarget

@Composable
fun ResultContent(vm: MainViewModel, result: ResultUi, savedToGallery: Boolean, busy: MainViewModel.Busy?) {
    val context = LocalContext.current
    val whatsapp = remember { Sharing.installedWhatsAppTargets(context) }
    val previewUri = remember(result.file) { Uri.fromFile(result.file) }

    fun shareResult(target: ShareTarget) {
        if (!Sharing.share(context, result.file, "video/mp4", target)) {
            vm.showNotice(context.getString(R.string.share_app_missing))
            Sharing.share(context, result.file, "video/mp4", ShareTarget.OTHER)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        VideoPreview(previewUri, result.output.aspectRatio)

        if (result.largerThanOriginal) {
            Banner(
                stringResource(R.string.larger_title),
                stringResource(R.string.larger_body, Formatters.bytes(result.outputBytes), Formatters.bytes(result.originalBytes)),
                BannerKind.Warning,
            )
        } else {
            val original = result.originalBytes
            if (original != null && original > 0) {
                val saved = original - result.outputBytes
                Text(
                    stringResource(R.string.smaller_note, Formatters.bytes(saved), (saved * 100 / original).toInt()),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        ComparisonCard(result.request.source, result.output, result.outputBytes)

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle(stringResource(R.string.share_title))
            Text(
                stringResource(R.string.share_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (ShareTarget.WHATSAPP in whatsapp) {
                PrimaryButton(stringResource(R.string.share_whatsapp), { shareResult(ShareTarget.WHATSAPP) })
            }
            if (ShareTarget.WHATSAPP_BUSINESS in whatsapp) {
                PrimaryButton(stringResource(R.string.share_business), { shareResult(ShareTarget.WHATSAPP_BUSINESS) })
            }
            if (whatsapp.isEmpty()) {
                PrimaryButton(stringResource(R.string.share_other), { shareResult(ShareTarget.OTHER) })
            } else {
                SecondaryButton(stringResource(R.string.share_other), { shareResult(ShareTarget.OTHER) })
            }
            SecondaryButton(
                text = when {
                    busy == MainViewModel.Busy.SAVING -> stringResource(R.string.saving)
                    savedToGallery -> stringResource(R.string.saved_gallery)
                    else -> stringResource(R.string.save_gallery)
                },
                onClick = vm::saveToGallery,
                enabled = !savedToGallery && busy == null,
            )
        }

        Card(shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle(stringResource(R.string.original_title))
                Text(stringResource(R.string.original_body), style = MaterialTheme.typography.bodyMedium)
                if (busy == MainViewModel.Busy.PREPARING_ORIGINAL) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.padding(4.dp))
                        Text(stringResource(R.string.preparing_original), style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    val target = whatsapp.firstOrNull() ?: ShareTarget.OTHER
                    SecondaryButton(stringResource(R.string.original_share), { vm.shareOriginal(target) }, enabled = busy == null)
                }
            }
        }
    }
}

@Composable
private fun ComparisonCard(before: VideoInfo, after: VideoInfo, afterBytes: Long) {
    Card(shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            SectionTitle(stringResource(R.string.compare_title), Modifier.padding(bottom = 8.dp))
            CompareHeader()
            HorizontalDivider()
            CompareRow(stringResource(R.string.spec_dimensions), Formatters.dimensions(before.width, before.height), Formatters.dimensions(after.width, after.height))
            CompareRow(stringResource(R.string.spec_duration), Formatters.duration(before.durationMs), Formatters.duration(after.durationMs))
            CompareRow(stringResource(R.string.spec_size), Formatters.bytes(before.sizeBytes), Formatters.bytes(afterBytes))
            CompareRow(stringResource(R.string.spec_video_codec), Formatters.videoCodec(before.videoMime), Formatters.videoCodec(after.videoMime))
            CompareRow(stringResource(R.string.spec_video_bitrate), bitrateText(before), bitrateText(after))
            CompareRow(stringResource(R.string.spec_fps), Formatters.frameRate(before.frameRate), Formatters.frameRate(after.frameRate))
            CompareRow(stringResource(R.string.spec_audio), audioText(before), audioText(after))
            Text(
                stringResource(R.string.compare_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun CompareHeader() {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Spacer(Modifier.weight(1f))
        Text(
            stringResource(R.string.compare_before),
            modifier = Modifier.weight(1.2f),
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.End,
        )
        Text(
            stringResource(R.string.compare_after),
            modifier = Modifier.weight(1.2f),
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun CompareRow(label: String, before: String, after: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(before, modifier = Modifier.weight(1.2f), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End)
        Text(after, modifier = Modifier.weight(1.2f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
    }
}

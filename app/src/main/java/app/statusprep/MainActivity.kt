package app.statusprep

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.statusprep.storage.Sharing
import app.statusprep.storage.ShareTarget
import app.statusprep.ui.MainScreen
import app.statusprep.ui.MainViewModel
import app.statusprep.ui.ResultScreen
import app.statusprep.ui.StatusPrepTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StatusPrepTheme {
                StatusPrepScreen(viewModel)
            }
        }
    }
}

@Composable
private fun StatusPrepScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val source by vm.source.collectAsStateWithLifecycle()
    val selection by vm.selection.collectAsStateWithLifecycle()
    val job by vm.job.collectAsStateWithLifecycle()
    val result by vm.result.collectAsStateWithLifecycle()
    val savedToGallery by vm.savedToGallery.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    val pendingShare by vm.pendingShare.collectAsStateWithLifecycle()

    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(notice) {
        notice?.let {
            snackbar.showSnackbar(it)
            vm.consumeNotice()
        }
    }
    LaunchedEffect(pendingShare) {
        pendingShare?.let { req ->
            vm.consumePendingShare()
            if (!Sharing.share(context, req.file, req.mime, req.target)) {
                vm.showNotice(context.getString(R.string.share_app_missing))
                Sharing.share(context, req.file, req.mime, ShareTarget.OTHER)
            }
        }
    }

    val current = result
    BackHandler(enabled = current != null) { vm.closeResult() }

    Crossfade(targetState = current, label = "screen") { shown ->
        if (shown != null) {
            ResultScreen(vm, shown, savedToGallery, busy, snackbar)
        } else {
            MainScreen(vm, source, selection, job, snackbar)
        }
    }
}

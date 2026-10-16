@file:OptIn(ExperimentalMaterial3Api::class)

package app.statusprep

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.statusprep.storage.Sharing
import app.statusprep.storage.ShareTarget
import app.statusprep.ui.MainContent
import app.statusprep.ui.MainViewModel
import app.statusprep.ui.ResultContent
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (current != null) stringResource(R.string.result_title) else stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    if (current != null) {
                        IconButton(onClick = vm::closeResult) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                if (current != null) {
                    ResultContent(vm, current, savedToGallery, busy)
                } else {
                    MainContent(vm, source, selection, job)
                }
            }
        }
    }
}

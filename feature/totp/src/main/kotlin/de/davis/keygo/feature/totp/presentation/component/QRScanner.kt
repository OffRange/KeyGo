package de.davis.keygo.feature.totp.presentation.component

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import de.davis.keygo.core.security.presentation.rememberHandoffStarter
import de.davis.keygo.feature.totp.R
import de.davis.keygo.feature.totp.domain.model.camera.Frame
import de.davis.keygo.feature.totp.domain.qr.QRScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun QRScanner(
    onClose: () -> Unit,
    success: (List<String>) -> Unit
) {
    BackHandler {
        onClose()
    }

    val softwareKeyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) {
        softwareKeyboardController?.hide()
        focusManager.clearFocus()
    }

    var permissionRequested by remember { mutableStateOf(false) }
    val permissionState = rememberPermissionState(Manifest.permission.CAMERA) {
        permissionRequested = true
    }

    when {
        permissionState.status.isGranted -> {
            ScannerContent(onClose = onClose, success = success)
        }

        permissionState.status.shouldShowRationale -> {
            ExplainPermissionDialog(
                onGrant = { permissionState.launchPermissionRequest() },
                onClose = onClose
            )
        }

        permissionRequested -> {
            // Permission was denied permanently (no rationale, not granted, already requested)
            val context = LocalContext.current
            val openSystemScreen = rememberHandoffStarter()
            PermissionDeniedDialog(
                onOpenSettings = {
                    openSystemScreen.launch(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null)
                        )
                    )
                },
                onClose = onClose
            )
        }

        else -> {
            // First time: request immediately
            LaunchedEffect(Unit) {
                permissionState.launchPermissionRequest()
            }
        }
    }
}

@Composable
private fun ExplainPermissionDialog(
    onGrant: () -> Unit,
    onClose: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {},
        modifier = Modifier.fillMaxWidth(),
        confirmButton = {
            Button(
                onClick = onGrant
            ) {
                Text(text = stringResource(R.string.grant))
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onClose
            ) {
                Text(text = stringResource(R.string.cancel))
            }
        },
        title = {
            Text(text = stringResource(R.string.permission_title))
        },
        text = {
            Text(text = stringResource(R.string.camera_permission_rationale))
        },
        icon = {
            Icon(
                imageVector = Icons.Default.Camera,
                contentDescription = null,
            )
        }
    )
}

@Composable
private fun PermissionDeniedDialog(
    onOpenSettings: () -> Unit,
    onClose: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {},
        modifier = Modifier.fillMaxWidth(),
        confirmButton = {
            Button(
                onClick = onOpenSettings
            ) {
                Text(text = stringResource(R.string.open_settings))
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onClose
            ) {
                Text(text = stringResource(R.string.cancel))
            }
        },
        title = {
            Text(text = stringResource(R.string.permission_title))
        },
        text = {
            Text(text = stringResource(R.string.permission_denied_permanently))
        },
        icon = {
            Icon(
                imageVector = Icons.Default.Camera,
                contentDescription = null,
            )
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScannerContent(
    onClose: () -> Unit,
    success: (List<String>) -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val cameraProvider = remember(context) {
        ProcessCameraProvider.getInstance(context).get()
    }

    val scanner = koinInject<QRScanner>()

    val preview = remember { Preview.Builder().build() }
    val imageAnalysis = remember {
        ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .apply {
                setAnalyzer(Dispatchers.Main.asExecutor()) { proxy ->
                    proxy.use {
                        val frame = Frame(
                            dataNv21 = proxy.toNv21(),
                            width = it.width,
                            height = it.height,
                            rotationDegrees = it.imageInfo.rotationDegrees,
                        )
                        scope.launch {
                            val codes = scanner.scan(frame = frame)
                            if (codes.isNotEmpty())
                                success(codes)
                        }
                    }
                }
            }
    }

    var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }
    LaunchedEffect(preview) {
        preview.setSurfaceProvider { request ->
            surfaceRequest = request
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        runCatching {
            cameraProvider.bindToLifecycle(
                lifecycleOwner = lifecycleOwner,
                cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageAnalysis
            )
        }

        onDispose {
            scanner.close()
            cameraProvider.unbindAll()
        }
    }

    surfaceRequest?.let {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopEnd
        ) {
            CameraXViewfinder(
                surfaceRequest = it,
                modifier = Modifier.fillMaxSize()
            )

            TopAppBar(
                title = {
                    Text(text = stringResource(R.string.scan_qr_code))
                },
                navigationIcon = {
                    IconButton(
                        onClick = onClose
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                        )
                    }
                }
            )
        }
    }
}


private fun ImageProxy.toNv21(): ByteArray {
    val yBuffer = planes[0].buffer
    val uBuffer = planes[1].buffer
    val vBuffer = planes[2].buffer

    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()

    val nv21 = ByteArray(ySize + uSize + vSize)

    // Y data first
    yBuffer.get(nv21, 0, ySize)

    // NV21 = VU interleaved
    vBuffer.get(nv21, ySize, vSize)
    uBuffer.get(nv21, ySize + vSize, uSize)

    return nv21
}
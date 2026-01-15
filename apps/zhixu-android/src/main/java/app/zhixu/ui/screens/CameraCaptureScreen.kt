package app.zhixu.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import app.zhixu.R
import app.zhixu.data.UiDoc
import app.zhixu.data.VaultRepository
import app.zhixu.ui.Ionicons
import app.zhixu.ui.ZhixuTopBarIconSize
import app.zhixu.ui.components.ZhixuIconButton
import app.zhixu.ui.components.ZhixuTopAppBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraCaptureScreen(
    vaultRootUri: Uri,
    repository: VaultRepository,
    onCreated: (UiDoc) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember(context) { ContextCompat.getMainExecutor(context) }

    var hasCameraPermission by
        remember {
            mutableStateOf(
                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
            )
        }
    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    val previewView =
        remember(context) {
            PreviewView(context).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
        }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var saving by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    DisposableEffect(hasCameraPermission) {
        if (!hasCameraPermission) return@DisposableEffect onDispose {}

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        val listener =
            Runnable {
                runCatching {
                    provider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                    val capture =
                        ImageCapture
                            .Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                            .build()
                    provider?.unbindAll()
                    provider?.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        capture,
                    )
                    imageCapture = capture
                }.onFailure {
                    errorText = it.message ?: context.getString(R.string.common_failed)
                }
            }

        cameraProviderFuture.addListener(listener, executor)

        onDispose {
            runCatching { provider?.unbindAll() }
        }
    }

    fun takePhoto() {
        val capture = imageCapture ?: return
        if (saving) return
        saving = true
        errorText = null

        val tempFile = File(context.cacheDir, "zhixu_camera_${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(tempFile).build()

        capture.takePicture(
            outputOptions,
            executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    scope.launch {
                        try {
                            val now = LocalDateTime.now()
                            val epochMs = System.currentTimeMillis()
                            val stamp = now.format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                            val vaultRelativePath = ".zhixu/camera/images/IMG_$epochMs.jpg"
                            val targetUri = repository.ensureVaultFile(vaultRootUri, vaultRelativePath, "image/jpeg")
                            val bytes = withContext(Dispatchers.IO) { tempFile.readBytes() }
                            repository.writeBytes(targetUri, bytes)

                            val created = repository.createDoc(vaultRootUri, "照片_$stamp")
                            val markdown =
                                buildString {
                                    append("# ").append(created.baseName).append("\n\n")
                                    append("![](").append(vaultRelativePath).append(")\n")
                                }
                            repository.writeText(created.uri, markdown)
                            runCatching { repository.indexDocUri(created.uri) }

                            onCreated(created)
                        } catch (t: Throwable) {
                            errorText = t.message ?: context.getString(R.string.common_failed)
                            saving = false
                        } finally {
                            runCatching { tempFile.delete() }
                            saving = false
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    errorText = exception.message ?: context.getString(R.string.common_failed)
                    runCatching { tempFile.delete() }
                    saving = false
                }
            },
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                ZhixuTopAppBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    title = { Text("拍照", style = MaterialTheme.typography.titleMedium) },
                    navigationIcon = {
                        ZhixuIconButton(onClick = onBack) {
                            androidx.compose.material3.Icon(
                                painter = painterResource(Ionicons.ArrowBack),
                                contentDescription = stringResource(R.string.action_back),
                                modifier = Modifier.size(ZhixuTopBarIconSize),
                            )
                        }
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        },
    ) { innerPadding ->
        Box(
            modifier =
                Modifier
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background)
                    .fillMaxSize(),
        ) {
            if (!hasCameraPermission) {
                Column(
                    modifier =
                        Modifier
                            .align(Alignment.Center)
                            .imePadding(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "需要相机权限才能拍照",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        modifier = Modifier.padding(top = 12.dp),
                    ) {
                        Text(text = "授权")
                    }
                }
            } else {
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier.fillMaxSize(),
                )

                FloatingActionButton(
                    onClick = ::takePhoto,
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 20.dp)
                            .windowInsetsPadding(WindowInsets.navigationBars),
                ) {
                    androidx.compose.material3.Icon(
                        painter = painterResource(R.drawable.ic_lucide_camera),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }

            val err = errorText
            if (!err.isNullOrBlank()) {
                Text(
                    text = err,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
                )
            }

            if (saving) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

package app.zhixu.ui.screens

import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.widget.Toast
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import app.zhixu.R
import app.zhixu.data.AccountPreferences
import app.zhixu.data.AccountState
import app.zhixu.sync.OfficialSync
import app.zhixu.sync.SyncServerClient
import app.zhixu.sync.SyncServerResult
import app.zhixu.ui.Ionicons
import app.zhixu.ui.ZhixuTopBarIconSize
import app.zhixu.ui.components.ZhixuIconButton
import app.zhixu.ui.components.ZhixuDialogDefaults
import app.zhixu.ui.components.ZhixuPasswordToggleIconButton
import app.zhixu.ui.components.ZhixuTextField
import app.zhixu.ui.components.ZhixuTopAppBar
import coil.compose.AsyncImage
import com.canhub.cropper.CropImageView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    contentPadding: PaddingValues,
    accountPrefs: AccountPreferences,
    onBack: () -> Unit,
    onOpenDeviceManagement: () -> Unit,
    onOpenStorageManagement: () -> Unit,
    onOpenSyncLogs: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by accountPrefs.state.collectAsState(
        initial = AccountState(token = "", username = "", userId = 0L, email = "", avatarUri = "", avatarUpdatedAtMs = 0L),
    )

    var showChangePassword by remember { mutableStateOf(false) }
    var authMode by remember { mutableStateOf(AuthMode.Login) }

    var avatarCropUri by remember { mutableStateOf<Uri?>(null) }
    var avatarCropLoading by remember { mutableStateOf(false) }
    var avatarCropLoadError by remember { mutableStateOf<String?>(null) }
    var avatarCropUploading by remember { mutableStateOf(false) }

    suspend fun cacheAvatarToInternal(
        userId: Long,
        updatedAtMs: Long,
        mime: String,
        bytes: ByteArray,
    ): String? =
        withContext(Dispatchers.IO) {
            if (bytes.isEmpty()) return@withContext null
            val ts = updatedAtMs.takeIf { it > 0L } ?: System.currentTimeMillis()
            val ext =
                when (mime.trim().lowercase()) {
                    "image/png" -> "png"
                    "image/jpeg" -> "jpg"
                    "image/webp" -> "webp"
                    "image/gif" -> "gif"
                    else -> "img"
                }
            val dir = File(context.filesDir, "avatars")
            if (!dir.exists()) dir.mkdirs()
            val safeUserId = state.userId.takeIf { it > 0L } ?: userId
            val fileName = "${safeUserId}_$ts.$ext"
            val file = File(dir, fileName)
            runCatching { file.writeBytes(bytes) }.getOrNull() ?: return@withContext null

            runCatching {
                dir.listFiles()?.forEach { f ->
                    if (f.name.startsWith("${safeUserId}_") && f.name != fileName) f.delete()
                }
            }
            Uri.fromFile(file).toString()
        }

    suspend fun uploadAvatarBytes(
        mime: String,
        bytes: ByteArray,
    ): Boolean {
        if (bytes.isEmpty()) {
            Toast.makeText(context, context.getString(R.string.account_avatar_pick_failed), Toast.LENGTH_SHORT).show()
            return false
        }
        val normalizedMime = mime.trim().ifBlank { "image/jpeg" }

        if (state.isLoggedIn) {
            val up = SyncServerClient.uploadAvatar(OfficialSync.BASE_URL, token = state.token, mime = normalizedMime, bytes = bytes)
            if (!up.ok || up.value == null) {
                val msg =
                    up.errorMessage
                        ?.takeIf { it.isNotBlank() && it != "NETWORK_UNREACHABLE" }
                        ?: context.getString(R.string.common_failed)
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                return false
            }
            val cached =
                cacheAvatarToInternal(
                    userId = state.userId,
                    updatedAtMs = up.value.updatedAtMs,
                    mime = up.value.mime.ifBlank { normalizedMime },
                    bytes = bytes,
                )
            if (!cached.isNullOrBlank()) {
                accountPrefs.setAvatarUri(cached, updatedAtMs = up.value.updatedAtMs)
                return true
            }
            Toast.makeText(context, context.getString(R.string.common_failed), Toast.LENGTH_SHORT).show()
            return false
        }

        val cached = cacheAvatarToInternal(userId = state.userId, updatedAtMs = 0L, mime = normalizedMime, bytes = bytes)
        if (!cached.isNullOrBlank()) {
            accountPrefs.setAvatarUri(cached)
            return true
        }
        Toast.makeText(context, context.getString(R.string.common_failed), Toast.LENGTH_SHORT).show()
        return false
    }

    fun uploadAvatarFromUri(uri: Uri) {
        scope.launch {
            val bytes =
                withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    }.getOrNull()
                } ?: ByteArray(0)
            val mime = context.contentResolver.getType(uri).orEmpty().ifBlank { "image/jpeg" }
            uploadAvatarBytes(mime = mime, bytes = bytes)
        }
    }

    LaunchedEffect(state.token) {
        if (!state.isLoggedIn) return@LaunchedEffect
        val me = SyncServerClient.me(OfficialSync.BASE_URL, token = state.token)
        val v = me.value ?: return@LaunchedEffect
        if (!me.ok) return@LaunchedEffect

        val serverUserId = v.userId.takeIf { it > 0L } ?: state.userId
        val serverUsername = v.username.trim().ifBlank { state.username }
        val serverEmail = v.email.orEmpty()

        if (serverUserId != state.userId || serverUsername != state.username) {
            accountPrefs.setLoggedIn(token = state.token, username = serverUsername, userId = serverUserId, email = serverEmail)
        }
        if (serverEmail != state.email) {
            accountPrefs.setEmail(serverEmail)
        }

        val avatarInfo = v.avatar
        if (avatarInfo != null && avatarInfo.hasAvatar) {
            val shouldDownload =
                !state.hasAvatar ||
                    (avatarInfo.updatedAtMs > 0L && avatarInfo.updatedAtMs != state.avatarUpdatedAtMs)
            if (shouldDownload) {
                val dl = SyncServerClient.downloadAvatar(OfficialSync.BASE_URL, token = state.token)
                val a = dl.value
                if (dl.ok && a != null && a.bytes.isNotEmpty()) {
                    val cached =
                        cacheAvatarToInternal(
                            userId = serverUserId,
                            updatedAtMs = a.updatedAtMs,
                            mime = a.mime,
                            bytes = a.bytes,
                        )
                    if (!cached.isNullOrBlank()) {
                        accountPrefs.setAvatarUri(cached, updatedAtMs = a.updatedAtMs)
                    }
                }
            }
        } else if (avatarInfo != null && !avatarInfo.hasAvatar && state.hasAvatar) {
            accountPrefs.setAvatarUri("")
        }
    }

    val pickLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            avatarCropUploading = false
            avatarCropUri = uri
        }

    val legacyPermission = Manifest.permission.READ_EXTERNAL_STORAGE
    val legacyPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                pickLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            } else {
                Toast.makeText(context, context.getString(R.string.account_avatar_permission_denied), Toast.LENGTH_SHORT).show()
            }
        }

    fun pickAvatar() {
        if (Build.VERSION.SDK_INT >= 33) {
            pickLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            return
        }
        val ok =
            ContextCompat.checkSelfPermission(context, legacyPermission) == PackageManager.PERMISSION_GRANTED
        if (ok) {
            pickLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        } else {
            legacyPermissionLauncher.launch(legacyPermission)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                ZhixuTopAppBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    title = { Text(stringResource(R.string.account_manage_title), style = MaterialTheme.typography.titleMedium) },
                    navigationIcon = {
                        ZhixuIconButton(onClick = onBack) {
                            Icon(
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
        if (!state.isLoggedIn) {
            Column(
                modifier =
                    Modifier
                        .padding(innerPadding)
                        .padding(contentPadding)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .fillMaxSize()
                        .imePadding()
                        .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp)) {
                    AuthForm(
                        accountPrefs = accountPrefs,
                        mode = authMode,
                        onModeChange = { authMode = it },
                        modifier = Modifier.fillMaxWidth(),
                        onAuthed = {},
                    )
                }
            }
            return@Scaffold
        }

        Column(
            modifier =
                Modifier
                    .padding(innerPadding)
                    .padding(contentPadding)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .fillMaxSize()
                    .imePadding(),
        ) {
            Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
                AccountRow(
                    title = stringResource(R.string.account_avatar),
                    enabled = true,
                    onClick = ::pickAvatar,
                    trailing = {
                        val uri = state.avatarUri
                        Box(
                            modifier =
                                Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (uri.isNotBlank()) {
                                AsyncImage(
                                    model = uri,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                Text(text = state.username.firstOrNull()?.uppercase() ?: "Z", style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

                AccountRow(
                    title = stringResource(R.string.account_avatar_remove),
                    enabled = state.hasAvatar,
                    onClick = {
                        scope.launch {
                            if (state.isLoggedIn) {
                                SyncServerClient.deleteAvatar(OfficialSync.BASE_URL, token = state.token)
                            }
                            accountPrefs.setAvatarUri("")
                        }
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

                AccountRow(
                    title = stringResource(R.string.account_nickname),
                    value = state.username.ifBlank { "-" },
                    enabled = false,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

                AccountRow(
                    title = stringResource(R.string.account_email),
                    value = state.email.ifBlank { "-" },
                    enabled = false,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

                AccountRow(
                    title = stringResource(R.string.account_change_password),
                    enabled = state.isLoggedIn,
                    onClick = { showChangePassword = true },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

                AccountRow(
                    title = stringResource(R.string.device_management_title),
                    enabled = state.isLoggedIn,
                    onClick = onOpenDeviceManagement,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

                AccountRow(
                    title = stringResource(R.string.account_storage_title),
                    enabled = state.isLoggedIn,
                    onClick = onOpenStorageManagement,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

                AccountRow(
                    title = stringResource(R.string.account_sync_logs_title),
                    enabled = state.isLoggedIn,
                    onClick = onOpenSyncLogs,
                )
            }

            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(8.dp),
                onClick = { scope.launch { accountPrefs.logout() } },
            ) {
                Text(stringResource(R.string.account_logout))
            }
        }
    }

    if (showChangePassword) {
        ChangePasswordDialog(
            token = state.token,
            onDismiss = { showChangePassword = false },
        )
    }


    val cropUri = avatarCropUri
    if (cropUri != null) {
        val cropView =
            remember(cropUri) {
                CropImageView(context).apply {
                    setFixedAspectRatio(true)
                    setAspectRatio(1, 1)
                    cropShape = CropImageView.CropShape.OVAL
                    isShowProgressBar = false
                }
            }

        fun decodeAvatarBitmap(bytes: ByteArray, maxEdgePx: Int = 2048): Bitmap? {
            if (bytes.isEmpty()) return null
            return runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                val w = bounds.outWidth
                val h = bounds.outHeight
                if (w <= 0 || h <= 0) return@runCatching null

                var sample = 1
                while (w / sample > maxEdgePx || h / sample > maxEdgePx) {
                    sample *= 2
                }

                while (sample <= 512) {
                    val opts =
                        BitmapFactory.Options().apply {
                            inSampleSize = sample
                            inPreferredConfig = Bitmap.Config.ARGB_8888
                        }
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                    if (bmp != null) return@runCatching bmp
                    sample *= 2
                }
                null
            }.getOrNull()
        }

        LaunchedEffect(cropUri) {
            avatarCropLoading = true
            avatarCropLoadError = null

            val bytes =
                withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(cropUri)?.use { it.readBytes() }
                    }.getOrNull()
                } ?: ByteArray(0)

            if (bytes.isEmpty()) {
                avatarCropLoadError = context.getString(R.string.account_avatar_pick_failed)
                avatarCropLoading = false
                return@LaunchedEffect
            }

            val bitmap =
                withContext(Dispatchers.Default) {
                    decodeAvatarBitmap(bytes)
                }

            if (bitmap == null) {
                avatarCropLoadError = context.getString(R.string.account_avatar_pick_failed)
                avatarCropLoading = false
                return@LaunchedEffect
            }

            cropView.setImageBitmap(bitmap)
            avatarCropLoading = false
        }

        AlertDialog(
            modifier = ZhixuDialogDefaults.modifier(),
            onDismissRequest = { avatarCropUri = null },
            properties = ZhixuDialogDefaults.properties,
            title = { Text(stringResource(R.string.account_avatar_crop_title)) },
            text = {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(360.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    AndroidView(
                        factory = { cropView },
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (avatarCropLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    val err = avatarCropLoadError
                    if (!err.isNullOrBlank()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(text = err, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !avatarCropUploading && !avatarCropLoading && avatarCropLoadError.isNullOrBlank(),
                    onClick = {
                        scope.launch {
                            avatarCropUploading = true
                            val bitmap = runCatching { cropView.getCroppedImage() }.getOrNull()
                            if (bitmap == null) {
                                avatarCropUploading = false
                                Toast.makeText(context, context.getString(R.string.account_avatar_pick_failed), Toast.LENGTH_SHORT).show()
                                return@launch
                            }
                            val bytes =
                                withContext(Dispatchers.Default) {
                                    ByteArrayOutputStream().use { out ->
                                        val ok = bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                                        if (!ok) return@withContext ByteArray(0)
                                        out.toByteArray()
                                    }
                                }
                            bitmap.recycle()

                            val ok = uploadAvatarBytes(mime = "image/jpeg", bytes = bytes)
                            avatarCropUploading = false
                            if (ok) avatarCropUri = null
                        }
                    },
                ) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !avatarCropUploading,
                    onClick = { avatarCropUri = null },
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun AccountRow(
    title: String,
    value: String? = null,
    enabled: Boolean,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val clickable = enabled && onClick != null
    ListItem(
        modifier =
            Modifier
                .fillMaxWidth()
                .let { m -> if (clickable) m.clickable(onClick = onClick!!) else m },
        headlineContent = { Text(title) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (trailing != null) {
                    trailing()
                } else if (!value.isNullOrBlank()) {
                    Text(
                        text = value,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(8.dp))
                } else {
                    Spacer(Modifier.width(8.dp))
                }
                Icon(
                    painter = painterResource(Ionicons.ChevronForward),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.4f),
                )
            }
        },
    )
}

@Composable
private fun ChangePasswordDialog(
    token: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    val serverUnreachableText = stringResource(R.string.error_server_unreachable)

    fun <T> SyncServerResult<T>.toUiMessage(fallback: String): String {
        return when {
            statusCode == 0 || errorMessage == "NETWORK_UNREACHABLE" -> serverUnreachableText
            !errorMessage.isNullOrBlank() -> errorMessage!!
            else -> fallback
        }
    }

    AlertDialog(
        modifier = ZhixuDialogDefaults.modifier(),
        onDismissRequest = { if (!busy) onDismiss() },
        properties = ZhixuDialogDefaults.properties,
        title = { Text(stringResource(R.string.account_change_password)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ZhixuTextField(
                    value = currentPassword,
                    onValueChange = { currentPassword = it },
                    enabled = !busy,
                    label = { Text(stringResource(R.string.account_current_password)) },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        ZhixuPasswordToggleIconButton(
                            show = showPassword,
                            onClick = { showPassword = !showPassword },
                        )
                    },
                )
                ZhixuTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    enabled = !busy,
                    label = { Text(stringResource(R.string.account_new_password)) },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                )
                ZhixuTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    enabled = !busy,
                    label = { Text(stringResource(R.string.account_confirm_password)) },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                )
                if (!status.isNullOrBlank()) {
                    Text(status!!, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && currentPassword.isNotBlank() && newPassword.isNotBlank() && confirmPassword.isNotBlank(),
                onClick = {
                    scope.launch {
                        if (token.isBlank()) {
                            status = context.getString(R.string.account_login_required)
                            return@launch
                        }
                        if (newPassword != confirmPassword) {
                            status = context.getString(R.string.account_password_mismatch)
                            return@launch
                        }
                        busy = true
                        status = null
                        try {
                            val res =
                                SyncServerClient.changePassword(
                                    baseUrl = OfficialSync.BASE_URL,
                                    token = token,
                                    currentPassword = currentPassword,
                                    newPassword = newPassword,
                                )
                            if (res.ok) {
                                Toast.makeText(context, context.getString(R.string.account_password_changed), Toast.LENGTH_SHORT).show()
                                onDismiss()
                            } else {
                                status = res.toUiMessage(context.getString(R.string.account_password_change_failed))
                            }
                        } finally {
                            busy = false
                        }
                    }
                },
            ) {
                Text(stringResource(R.string.action_confirm))
            }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

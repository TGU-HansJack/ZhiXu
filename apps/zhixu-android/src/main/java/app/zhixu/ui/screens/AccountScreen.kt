package app.zhixu.ui.screens

import android.Manifest
import android.os.Build
import android.widget.Toast
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.canhub.cropper.CropImageView
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    contentPadding: PaddingValues,
    accountPrefs: AccountPreferences,
    onBack: () -> Unit,
    onOpenDeviceManagement: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by accountPrefs.state.collectAsState(
        initial = AccountState(token = "", username = "", userId = 0L, email = "", avatarUri = ""),
    )

    var showChangePassword by remember { mutableStateOf(false) }
    var authMode by remember { mutableStateOf(AuthMode.Login) }

    val cropLauncher =
        rememberLauncherForActivityResult(CropImageContract()) { result ->
            if (result.isSuccessful) {
                val uri = result.uriContent
                if (uri != null) {
                    scope.launch { accountPrefs.setAvatarUri(uri.toString()) }
                }
            } else {
                val msg = result.error?.message.orEmpty().ifBlank { context.getString(R.string.account_avatar_pick_failed) }
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        }

    val pickLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            val opts =
                CropImageContractOptions(
                    uri,
                    CropImageOptions(
                        fixAspectRatio = true,
                        aspectRatioX = 1,
                        aspectRatioY = 1,
                        cropShape = CropImageView.CropShape.OVAL,
                        outputCompressQuality = 90,
                    ),
                )
            cropLauncher.launch(opts)
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

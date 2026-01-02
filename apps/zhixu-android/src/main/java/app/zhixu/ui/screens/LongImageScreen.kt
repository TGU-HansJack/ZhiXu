package com.zhixu.android.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import androidx.core.content.FileProvider
import com.zhixu.android.R
import com.zhixu.android.data.SyncPreferences
import com.zhixu.android.ui.Ionicons
import com.zhixu.android.data.WebDavConfig
import com.zhixu.android.ui.longimage.LongImageRenderInput
import com.zhixu.android.ui.longimage.renderLongImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LongImageScreen(
    markdown: String,
    vaultRootUri: Uri?,
    fontScale: Float,
    title: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val appName = stringResource(R.string.app_name)

    val syncPrefs = remember(context) { SyncPreferences(context) }
    val webDavConfig by syncPrefs.webDavConfig.collectAsState(
        initial =
            WebDavConfig(
                enabled = false,
                baseUrl = "",
                username = "",
                password = "",
                remoteRoot = "/",
                includeIndexSqlite = false,
            ),
    )
    val userName = webDavConfig.username.trim().ifBlank { "用户" }

    val palette =
        remember {
            listOf(
                Color(0xFFFFFFFF),
                Color(0xFFF7F5F2),
                Color(0xFFF2F6F1),
                Color(0xFFF1F4FA),
                Color(0xFFF8F1F6),
                Color(0xFFF6F2EE),
                Color(0xFFF0E9DD),
            )
        }
    var bgColorArgb by rememberSaveable { mutableIntStateOf(palette.first().toArgb()) }
    val bgColor = Color(bgColorArgb)
    var showWatermark by rememberSaveable { mutableStateOf(true) }
    var bottomTab by rememberSaveable { mutableIntStateOf(1) } // 0:样式 1:颜色 2:其他

    var isGenerating by remember { mutableStateOf(false) }
    var baseBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showFullPreview by remember { mutableStateOf(false) }

    val themeColors = MaterialTheme.colorScheme
    val themeJson =
        remember(bgColorArgb, themeColors) {
            PreviewThemePayload(
                isDark = bgColorArgb.isProbablyDark(),
                surface = bgColor.toCssHex(),
                onSurface = themeColors.onSurface.toCssHex(),
                outline = themeColors.outline.copy(alpha = 0.35f).toCssHex(),
                quoteBg = themeColors.surfaceVariant.copy(alpha = 0.55f).toCssHex(),
                link = themeColors.primary.toCssHex(),
                inlineCodeBg = themeColors.secondaryContainer.copy(alpha = 0.55f).toCssHex(),
                inlineCodeFg = themeColors.onSecondaryContainer.toCssHex(),
            ).toJson()
        }

    LaunchedEffect(markdown, vaultRootUri, fontScale, bgColor, themeJson) {
        isGenerating = true
        previewBitmap = null
        baseBitmap =
            runCatching {
                val widthPx = (context.resources.displayMetrics.widthPixels * 0.92f).roundToInt().coerceAtLeast(1)
                renderLongImage(
                    LongImageRenderInput(
                        context = context.applicationContext,
                        markdown = markdown,
                        vaultRootUri = vaultRootUri,
                        themeJson = themeJson,
                        fontScale = fontScale,
                        backgroundArgb = bgColorArgb,
                        targetWidthPx = widthPx,
                    ),
                )
            }.onFailure {
                snackbarHostState.showSnackbar("生成失败")
            }.getOrNull()
        isGenerating = false
    }

    LaunchedEffect(baseBitmap, showWatermark, userName, appName) {
        val base = baseBitmap ?: return@LaunchedEffect
        previewBitmap =
            if (!showWatermark) {
                base
            } else {
                withContext(Dispatchers.Default) {
                    base.copy(Bitmap.Config.ARGB_8888, true).apply {
                        drawWatermark(
                            bitmap = this,
                            leftText = userName,
                            rightText = appName,
                            density = context.resources.displayMetrics.density,
                        )
                    }
                }
            }
    }

    fun share(bitmap: Bitmap) {
        scope.launch {
            val uri =
                withContext(Dispatchers.IO) {
                    val dir = File(context.cacheDir, "share").apply { mkdirs() }
                    val file = File(dir, "long_image_${System.currentTimeMillis()}.png")
                    FileOutputStream(file).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                }

            val intent =
                Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_SUBJECT, title.ifBlank { appName })
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            runCatching {
                context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.onFailure {
                snackbarHostState.showSnackbar("无法打开分享面板")
            }
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    windowInsets = TopAppBarDefaults.windowInsets,
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
                    title = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "文本",
                                modifier = Modifier.padding(horizontal = 10.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "图片",
                                modifier = Modifier.padding(horizontal = 10.dp),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(painter = painterResource(Ionicons.Close), contentDescription = null)
                        }
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .background(Color(0xFFF6F6F6))
                    .imePadding()
                    .windowInsetsPadding(WindowInsets.navigationBars),
        ) {
            val bmp = previewBitmap
            val previewScrollState = rememberScrollState()
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                Surface(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = bmp != null && !isGenerating) { showFullPreview = true },
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                ) {
                    if (bmp == null) {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(280.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(text = if (isGenerating) "生成中…" else "暂无预览", textAlign = TextAlign.Center)
                        }
                    } else {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(previewScrollState),
                        ) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxWidth(),
                                contentScale = ContentScale.FillWidth,
                            )
                        }
                    }
                }
            }

            Surface(color = Color.White, tonalElevation = 2.dp) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    TabRow(
                        selectedTabIndex = bottomTab,
                        containerColor = Color.White,
                    ) {
                        Tab(
                            selected = bottomTab == 0,
                            onClick = { bottomTab = 0 },
                            text = { Text("样式") },
                        )
                        Tab(
                            selected = bottomTab == 1,
                            onClick = { bottomTab = 1 },
                            text = { Text("颜色") },
                        )
                        Tab(
                            selected = bottomTab == 2,
                            onClick = { bottomTab = 2 },
                            text = { Text("其他") },
                        )
                    }

                    when (bottomTab) {
                        1 -> {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                items(palette.size) { idx ->
                                    val c = palette[idx]
                                    val selected = c.toArgb() == bgColorArgb
                                    Box(
                                        modifier =
                                            Modifier
                                                .size(44.dp)
                                                .background(Color.Transparent, CircleShape)
                                                .clickable(enabled = !isGenerating) { bgColorArgb = c.toArgb() },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Surface(
                                            modifier = Modifier.size(40.dp),
                                            shape = CircleShape,
                                            color = c,
                                            shadowElevation = 0.dp,
                                            tonalElevation = 0.dp,
                                        ) {}
                                        if (selected) {
                                            Surface(
                                                modifier = Modifier.size(18.dp),
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.primary,
                                                tonalElevation = 0.dp,
                                                shadowElevation = 0.dp,
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        painter = painterResource(Ionicons.Checkmark),
                                                        contentDescription = null,
                                                        tint = Color.White,
                                                        modifier = Modifier.size(12.dp),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        2 -> {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(text = "水印", modifier = Modifier.weight(1f))
                                Switch(checked = showWatermark, onCheckedChange = { showWatermark = it })
                            }
                        }

                        else -> {
                            Text(
                                text = "样式选项后续补充",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                            )
                        }
                    }

                    Button(
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        onClick = {
                            val toShare = previewBitmap
                            if (toShare == null) {
                                scope.launch { snackbarHostState.showSnackbar("暂无可分享的图片") }
                            } else {
                                share(toShare)
                            }
                        },
                        enabled = !isGenerating && previewBitmap != null,
                    ) {
                        Text(text = "分享")
                    }

                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }

    if (showFullPreview && previewBitmap != null) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showFullPreview = false }) {
            val fullScrollState = rememberScrollState()
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .clickable { showFullPreview = false },
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(fullScrollState)
                            .padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(
                        bitmap = previewBitmap!!.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = ContentScale.FillWidth,
                    )
                }
            }
        }
    }
}

private data class PreviewThemePayload(
    val isDark: Boolean,
    val surface: String,
    val onSurface: String,
    val outline: String,
    val quoteBg: String,
    val link: String,
    val inlineCodeBg: String,
    val inlineCodeFg: String,
) {
    fun toJson(): String =
        """
        {
          "isDark": $isDark,
          "surface": "$surface",
          "onSurface": "$onSurface",
          "outline": "$outline",
          "quoteBg": "$quoteBg",
          "link": "$link",
          "inlineCodeBg": "$inlineCodeBg",
          "inlineCodeFg": "$inlineCodeFg"
        }
        """.trimIndent()
}

private fun Color.toCssHex(): String {
    val rgb = this.toArgb() and 0x00FFFFFF
    return String.format("#%06X", rgb)
}

private fun Int.isProbablyDark(): Boolean {
    val r = (this shr 16) and 0xFF
    val g = (this shr 8) and 0xFF
    val b = this and 0xFF
    val luma = (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255.0
    return luma < 0.5
}

private fun drawWatermark(
    bitmap: Bitmap,
    leftText: String,
    rightText: String,
    density: Float,
) {
    val canvas = android.graphics.Canvas(bitmap)
    val paint =
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(190, 160, 160, 160)
            textSize = 12f * density
        }
    val pad = 12f * density
    val baseline = bitmap.height - pad
    canvas.drawText(leftText, pad, baseline, paint)
    val rightWidth = paint.measureText(rightText)
    canvas.drawText(rightText, bitmap.width - pad - rightWidth, baseline, paint)
}

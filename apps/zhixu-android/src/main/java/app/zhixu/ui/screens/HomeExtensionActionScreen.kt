package app.zhixu.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ListItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.zhixu.plugins.PluginRepository
import app.zhixu.plugins.runtime.JsPluginRuntime
import app.zhixu.ui.Ionicons
import app.zhixu.ui.components.ZhixuIconButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private data class ListPage(
    val title: String?,
    val items: List<ListPageItem>,
)

private data class ListPageItem(
    val title: String,
    val subtitle: String?,
    val checked: Boolean?,
    val docUri: String?,
    val lineIndex: Int?,
    val toggleActionId: String?,
    val toggleInput: Map<*, *>?,
    val clickActionId: String?,
    val clickInput: Map<*, *>?,
)

@Composable
fun HomeExtensionActionScreen(
    contentPadding: PaddingValues,
    vaultRootUri: Uri?,
    pluginRepo: PluginRepository,
    pluginId: String,
    actionId: String,
    onOpenDoc: (String, Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val runtime =
        remember(context, pluginRepo) {
            JsPluginRuntime(
                appContext = context.applicationContext,
                pluginRepo = pluginRepo,
            )
        }
    val listBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.70f)

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .padding(contentPadding)
                .background(listBg),
    ) {
        if (vaultRootUri == null) {
            Text(
                text = "请先选择空间（Vault）",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center),
            )
            return
        }

        HomeExtensionActionPage(
            rootUri = vaultRootUri,
            runtime = runtime,
            pluginId = pluginId,
            actionId = actionId,
            onOpenDoc = onOpenDoc,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun HomeExtensionActionPage(
    rootUri: Uri,
    runtime: JsPluginRuntime,
    pluginId: String,
    actionId: String,
    onOpenDoc: (String, Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var refreshToken by remember { mutableLongStateOf(0L) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var page by remember { mutableStateOf<ListPage?>(null) }

    fun parsePage(any: Any?): ListPage? {
        val map = any as? Map<*, *> ?: return null
        val pageMap = map["page"] as? Map<*, *> ?: return null
        val title = pageMap["title"]?.toString()?.takeIf { it.isNotBlank() }
        val itemsRaw = pageMap["items"] as? List<*> ?: emptyList<Any?>()
        val items =
            itemsRaw.mapNotNull { raw ->
                val m = raw as? Map<*, *> ?: return@mapNotNull null
                val t = m["title"]?.toString()?.trim().orEmpty()
                if (t.isBlank()) return@mapNotNull null
                ListPageItem(
                    title = t,
                    subtitle = m["subtitle"]?.toString()?.takeIf { it.isNotBlank() },
                    checked = (m["checked"] as? Boolean),
                    docUri = m["docUri"]?.toString()?.takeIf { it.isNotBlank() },
                    lineIndex = (m["lineIndex"] as? Number)?.toInt(),
                    toggleActionId = m["toggleActionId"]?.toString()?.takeIf { it.isNotBlank() },
                    toggleInput = m["toggleInput"] as? Map<*, *>,
                    clickActionId = m["clickActionId"]?.toString()?.takeIf { it.isNotBlank() },
                    clickInput = m["clickInput"] as? Map<*, *>,
                )
            }
        return ListPage(title = title, items = items)
    }

    fun toJsonValue(v: Any?): Any {
        return when (v) {
            null -> JSONObject.NULL
            is JSONObject, is JSONArray -> v
            is Map<*, *> -> {
                val obj = JSONObject()
                for ((k, value) in v) {
                    if (k == null) continue
                    obj.put(k.toString(), toJsonValue(value))
                }
                obj
            }
            is List<*> -> {
                val arr = JSONArray()
                for (x in v) arr.put(toJsonValue(x))
                arr
            }
            is Boolean, is Number, is String -> v
            else -> v.toString()
        }
    }

    fun load() {
        loading = true
        error = null
        page = null
        scope.launch {
            val res =
                withContext(Dispatchers.IO) {
                    runtime.runAction(
                        rootUri = rootUri,
                        pluginId = pluginId,
                        actionId = actionId,
                        input = JSONObject(),
                    )
                }
            loading = false
            if (!res.ok) {
                error = res.message
                return@launch
            }
            val parsed = parsePage(res.data)
            if (parsed == null) {
                error = "插件未返回可渲染的 page 数据"
                return@launch
            }
            page = parsed
        }
    }

    LaunchedEffect(pluginId, actionId, rootUri.toString(), refreshToken) {
        load()
    }

    if (loading) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(text = "加载中…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    if (error != null) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                Text(text = "加载失败：${error.orEmpty()}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(onClick = { refreshToken += 1L }) { Text(text = "重试") }
            }
        }
        return
    }

    val p = page ?: return

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(p.items.size) { idx ->
            val item = p.items[idx]
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(8.dp),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                ListItem(
                    modifier =
                        if (item.docUri != null || item.clickActionId != null) {
                            Modifier.clickable {
                                when {
                                    item.clickActionId != null -> {
                                        val payload = (item.clickInput ?: emptyMap<Any?, Any?>()).let(::toJsonValue) as JSONObject
                                        scope.launch {
                                            withContext(Dispatchers.IO) {
                                                runtime.runAction(
                                                    rootUri = rootUri,
                                                    pluginId = pluginId,
                                                    actionId = item.clickActionId,
                                                    input = payload,
                                                )
                                            }
                                            refreshToken += 1L
                                        }
                                    }
                                    item.docUri != null -> onOpenDoc(item.docUri, item.lineIndex)
                                }
                            }
                        } else {
                            Modifier
                        },
                    headlineContent = { Text(text = item.title) },
                    supportingContent = {
                        item.subtitle?.let { Text(text = it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    },
                    leadingContent = {
                        if (item.checked != null) {
                            val icon = if (item.checked == true) Ionicons.CircleCheck else Ionicons.Circle
                            Icon(
                                painter = painterResource(icon),
                                contentDescription = null,
                                tint = if (item.checked == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    trailingContent = {
                        if (item.toggleActionId != null) {
                            ZhixuIconButton(
                                onClick = {
                                    val base = item.toggleInput ?: emptyMap<Any?, Any?>()
                                    val payload =
                                        toJsonValue(
                                            base +
                                                mapOf(
                                                    "docUri" to item.docUri,
                                                    "lineIndex" to item.lineIndex,
                                                ),
                                        ) as JSONObject
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            runtime.runAction(
                                                rootUri = rootUri,
                                                pluginId = pluginId,
                                                actionId = item.toggleActionId,
                                                input = payload,
                                            )
                                        }
                                        refreshToken += 1L
                                    }
                                },
                            ) {
                                Icon(
                                    painter = painterResource(Ionicons.CheckmarkCircle),
                                    contentDescription = "切换",
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                )
            }
        }
    }
}


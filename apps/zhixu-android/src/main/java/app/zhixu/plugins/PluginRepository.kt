package com.zhixu.android.plugins

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.zhixu.android.data.vaultRootToDocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.eclipse.jgit.api.Git
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class PluginManifest(
    val id: String,
    val name: String?,
    val version: String?,
    val description: String?,
    val entry: String? = null,
    val files: List<String> = emptyList(),
    val actions: List<PluginActionSpec> = emptyList(),
)

data class PluginActionSpec(
    val id: String,
    val label: String,
    val icon: String? = null,
    val place: String? = null,
    val ringIndex: Int? = null,
)

data class InstalledPlugin(
    val manifest: PluginManifest,
    val enabled: Boolean,
)

data class PluginSource(
    val type: String,
    val url: String? = null,
)

data class InstallResult(
    val ok: Boolean,
    val message: String,
    val pluginId: String? = null,
)

class PluginRepository(
    private val context: Context,
    private val http: OkHttpClient = OkHttpClient(),
) {
    private val officialBaseUrl = "https://zhixu.app"
    private val tag = "PluginRepository"

    suspend fun listInstalled(rootUri: Uri): List<InstalledPlugin> = withContext(Dispatchers.IO) {
        val pluginsDir = ensurePluginsDir(rootUri) ?: return@withContext emptyList()
        val enabledIds = readEnabledSet(pluginsDir)

        val out = ArrayList<InstalledPlugin>()
        val seenIds = HashSet<String>()
        for (dir in pluginsDir.listFiles().filter { it.isDirectory }) {
            val manifest = readManifest(dir) ?: continue

            val folderName = dir.name?.trim().orEmpty()
            if (folderName.isBlank()) continue

            if (folderName != manifest.id) {
                Log.w(tag, "Skipping plugin folder '$folderName': manifest.id='${manifest.id}' (folder must match id)")
                continue
            }
            if (!seenIds.add(manifest.id)) {
                Log.w(tag, "Duplicate installed plugin id skipped: ${manifest.id}")
                continue
            }
            out +=
                InstalledPlugin(
                    manifest = manifest,
                    enabled = enabledIds.contains(manifest.id),
                )
        }
        out.sortedBy { (it.manifest.name ?: it.manifest.id).lowercase() }
    }

    suspend fun setEnabled(rootUri: Uri, pluginId: String, enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        val pluginsDir = ensurePluginsDir(rootUri) ?: return@withContext false
        val stateFile = ensurePluginStateFile(pluginsDir) ?: return@withContext false
        val state = readJsonObject(stateFile.uri) ?: JSONObject()
        val enabledArray = state.optJSONArray("enabled") ?: JSONArray()
        val set = enabledArray.toStringList().toMutableSet()
        if (enabled) set.add(pluginId) else set.remove(pluginId)
        state.put("enabled", JSONArray(set.toList().sorted()))
        writeJsonObject(stateFile.uri, state)
        true
    }

    suspend fun removePlugin(rootUri: Uri, pluginId: String): Boolean = withContext(Dispatchers.IO) {
        val pluginsDir = ensurePluginsDir(rootUri) ?: return@withContext false
        val pluginDir = pluginsDir.findFile(pluginId) ?: return@withContext false
        val removed = pluginDir.delete()
        if (removed) {
            setEnabled(rootUri, pluginId, enabled = false)
            clearPluginSource(pluginsDir, pluginId)
        }
        removed
    }

    suspend fun installFromLocalFolder(rootUri: Uri, folderUri: Uri): InstallResult = withContext(Dispatchers.IO) {
        val pluginsDir = ensurePluginsDir(rootUri) ?: return@withContext InstallResult(false, "Missing plugins directory")
        val sourceDir = DocumentFile.fromTreeUri(context, folderUri)
            ?.takeIf { it.isDirectory }
            ?: return@withContext InstallResult(false, "Invalid folder")

        val sourceManifestFile = sourceDir.findFile("manifest.json")
            ?.takeIf { it.isFile }
            ?: return@withContext InstallResult(false, "manifest.json not found in selected folder")
        val manifestText = readText(sourceManifestFile.uri) ?: return@withContext InstallResult(false, "Failed to read manifest.json")
        val manifest = parseManifestFromText(manifestText) ?: return@withContext InstallResult(false, "Invalid manifest.json")

        val pluginId = manifest.id
        if (pluginsDir.findFile(pluginId) != null) return@withContext InstallResult(false, "Plugin already installed", pluginId)
        val destDir = pluginsDir.createDirectory(pluginId)
            ?: return@withContext InstallResult(false, "Failed to create plugin folder: $pluginId", pluginId)

        val ok = copyDocumentDirRecursively(sourceDir, destDir)
        if (!ok) {
            destDir.delete()
            return@withContext InstallResult(false, "Failed to copy plugin files", pluginId)
        }
        val result = InstallResult(true, "Installed: ${manifest.name ?: manifest.id}", pluginId)
        recordPluginSource(rootUri, pluginId, PluginSource(type = "local"))
        result
    }

    suspend fun installFromGitUrl(rootUri: Uri, gitUrl: String): InstallResult = withContext(Dispatchers.IO) {
        val pluginsDir = ensurePluginsDir(rootUri) ?: return@withContext InstallResult(false, "Missing plugins directory")
        val tmp = File(context.cacheDir, "plugins-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            runCatching {
                Git.cloneRepository()
                    .setURI(gitUrl.trim())
                    .setDirectory(tmp)
                    .call()
            }.getOrElse { e ->
                return@withContext InstallResult(false, "Git clone failed: ${e.message ?: e.javaClass.simpleName}")
            }

            val manifestFile = File(tmp, "manifest.json")
            if (!manifestFile.exists()) return@withContext InstallResult(false, "manifest.json not found at repo root")
            val manifestText = runCatching { manifestFile.readText(Charsets.UTF_8) }
                .getOrElse { return@withContext InstallResult(false, "Failed to read manifest.json") }
            val manifest = parseManifestFromText(manifestText)
                ?: return@withContext InstallResult(false, "Invalid manifest.json")

            val pluginId = manifest.id
            if (pluginsDir.findFile(pluginId) != null) return@withContext InstallResult(false, "Plugin already installed", pluginId)
            val destDir = pluginsDir.createDirectory(pluginId)
                ?: return@withContext InstallResult(false, "Failed to create plugin folder: $pluginId", pluginId)

            val ok = copyFileDirRecursively(tmp, destDir) { file ->
                val name = file.name
                name != ".git" && name != ".github"
            }
            if (!ok) {
                destDir.delete()
                return@withContext InstallResult(false, "Failed to copy plugin files", pluginId)
            }

            val result = InstallResult(true, "Installed: ${manifest.name ?: manifest.id}", pluginId)
            recordPluginSource(rootUri, pluginId, PluginSource(type = "git", url = gitUrl.trim()))
            result
        } finally {
            tmp.deleteRecursively()
        }
    }

    suspend fun listOfficialPluginIds(): Result<List<String>> =
        withContext(Dispatchers.IO) {
            val endpoints =
                listOf(
                    "$officialBaseUrl/plugins/index.json",
                    "$officialBaseUrl/plugins/plugins.json",
                    "$officialBaseUrl/plugins/list.json",
                )
            for (url in endpoints) {
                val text = httpGetText(url) ?: continue
                val ids =
                    runCatching {
                        val arr = JSONArray(text)
                        (0 until arr.length()).mapNotNull { idx -> arr.optString(idx).trim().takeIf { it.isNotBlank() } }
                    }.getOrNull()
                if (!ids.isNullOrEmpty()) return@withContext Result.success(ids.distinct())
            }
            Result.failure(IllegalStateException("Official plugin index not available"))
        }

    suspend fun fetchOfficialManifest(pluginId: String): PluginManifest? =
        withContext(Dispatchers.IO) {
            val url = "$officialBaseUrl/plugins/${pluginId.trim()}/manifest.json"
            val text = httpGetText(url) ?: return@withContext null
            parseManifestFromText(text)
        }

    suspend fun installFromOfficial(rootUri: Uri, pluginId: String): InstallResult =
        withContext(Dispatchers.IO) {
            val pluginsDir = ensurePluginsDir(rootUri) ?: return@withContext InstallResult(false, "Missing plugins directory")
            val id = pluginId.trim()
            if (id.isBlank()) return@withContext InstallResult(false, "Invalid plugin id")
            if (pluginsDir.findFile(id) != null) return@withContext InstallResult(false, "Plugin already installed", id)

            val base = "$officialBaseUrl/plugins/$id"
            val manifestText = httpGetText("$base/manifest.json") ?: return@withContext InstallResult(false, "Failed to download manifest.json", id)
            val manifestJson = runCatching { JSONObject(manifestText) }.getOrNull() ?: return@withContext InstallResult(false, "Invalid manifest.json", id)
            val manifest = parseManifestFromText(manifestText) ?: return@withContext InstallResult(false, "Invalid manifest.json", id)

            val destDir = pluginsDir.createDirectory(id) ?: return@withContext InstallResult(false, "Failed to create plugin folder: $id", id)
            try {
                val required = LinkedHashSet<String>()
                required += "manifest.json"
                required += resolveEntryFileNames(manifest)
                required += manifest.files

                for (name in required) {
                    val bytes = httpGetBytes("$base/$name") ?: return@withContext InstallResult(false, "Failed to download: $name", id)
                    val outFile = findFileLoose(destDir, name) ?: createFilePreservingName(destDir, mimeTypeForName(name), name) ?: return@withContext InstallResult(false, "Failed to create: $name", id)
                    if (!writeBytes(outFile.uri, bytes)) return@withContext InstallResult(false, "Failed to write: $name", id)
                }

                // Optional docs
                val readmeNames = listOf("README.md", "readme.md")
                for (n in readmeNames) {
                    val bytes = httpGetBytes("$base/$n") ?: continue
                    val outFile = findFileLoose(destDir, n) ?: createFilePreservingName(destDir, mimeTypeForName(n), n) ?: continue
                    writeBytes(outFile.uri, bytes)
                    break
                }

                val result = InstallResult(true, "Installed: ${manifest.name ?: manifest.id}", manifest.id)
                recordPluginSource(rootUri, manifest.id, PluginSource(type = "official"))
                result
            } catch (e: Throwable) {
                destDir.delete()
                InstallResult(false, "Install failed: ${e.message ?: e.javaClass.simpleName}", id)
            }
        }

    suspend fun updatePlugin(rootUri: Uri, pluginId: String): InstallResult =
        withContext(Dispatchers.IO) {
            val pluginsDir = ensurePluginsDir(rootUri) ?: return@withContext InstallResult(false, "Missing plugins directory")
            val id = pluginId.trim()
            if (id.isBlank()) return@withContext InstallResult(false, "Invalid plugin id")
            val destDir = pluginsDir.findFile(id) ?: return@withContext InstallResult(false, "Plugin not installed: $id", id)

            val currentConfig = readPluginConfig(rootUri, id) ?: JSONObject()
            val source = readPluginSource(pluginsDir, id)

            fun mergeAndWriteConfig(defaults: JSONObject?) {
                val merged =
                    if (defaults == null) {
                        currentConfig
                    } else {
                        deepMergeDefaults(defaults, currentConfig)
                    }
                val configFile = ensurePluginConfigFile(rootUri, id, "config.json") ?: return
                writeJsonObject(configFile.uri, merged)
            }

            when (source?.type?.lowercase()) {
                "git" -> {
                    val url = source.url?.trim().orEmpty()
                    if (url.isBlank()) return@withContext InstallResult(false, "Missing git url for plugin: $id", id)
                    syncFromGitIntoExisting(pluginId = id, gitUrl = url, destDir = destDir, writeMergedConfig = ::mergeAndWriteConfig)
                }

                "official" -> {
                    syncFromOfficialIntoExisting(pluginId = id, destDir = destDir, writeMergedConfig = ::mergeAndWriteConfig)
                }

                "local" -> {
                    InstallResult(false, "This plugin was installed from a local folder and cannot be updated automatically.", id)
                }

                else -> {
                    val officialManifest = fetchOfficialManifest(id)
                    if (officialManifest != null) {
                        syncFromOfficialIntoExisting(pluginId = id, destDir = destDir, writeMergedConfig = ::mergeAndWriteConfig)
                    } else {
                        InstallResult(false, "Update not available: unknown install source.", id)
                    }
                }
            }.also { result ->
                if (result.ok) {
                    // Ensure source is recorded after a successful update.
                    when (source?.type?.lowercase()) {
                        "git" -> recordPluginSource(rootUri, id, PluginSource(type = "git", url = source.url?.trim()))
                        "official", null -> recordPluginSource(rootUri, id, PluginSource(type = "official"))
                    }
                }
            }
        }

    suspend fun readPluginConfig(rootUri: Uri, pluginId: String, fileName: String = "config.json"): JSONObject? =
        withContext(Dispatchers.IO) {
            val file = ensurePluginConfigFile(rootUri, pluginId, fileName) ?: return@withContext null
            readJsonObject(file.uri)
        }

    suspend fun readPluginManifest(rootUri: Uri, pluginId: String): PluginManifest? =
        withContext(Dispatchers.IO) {
            val pluginsDir = ensurePluginsDir(rootUri) ?: return@withContext null
            val pluginDir = pluginsDir.findFile(pluginId) ?: return@withContext null
            readManifest(pluginDir)
        }

    suspend fun writePluginConfig(rootUri: Uri, pluginId: String, json: JSONObject, fileName: String = "config.json"): Boolean =
        withContext(Dispatchers.IO) {
            val file = ensurePluginConfigFile(rootUri, pluginId, fileName) ?: return@withContext false
            writeJsonObject(file.uri, json)
            true
        }

    suspend fun readPluginFileText(rootUri: Uri, pluginId: String, fileName: String): String? =
        withContext(Dispatchers.IO) {
            val pluginsDir = ensurePluginsDir(rootUri) ?: return@withContext null
            val pluginDir = pluginsDir.findFile(pluginId) ?: return@withContext null
            val file = findFileLoose(pluginDir, fileName)?.takeIf { it.isFile } ?: return@withContext null
            readText(file.uri)
        }

    suspend fun readPluginReadme(rootUri: Uri, pluginId: String): String? =
        readPluginFileText(rootUri, pluginId, "README.md") ?: readPluginFileText(rootUri, pluginId, "readme.md")

    private fun ensurePluginsDir(rootUri: Uri): DocumentFile? {
        val root = vaultRootToDocumentFile(context, rootUri) ?: return null
        val zhixu = root.findFile(".zhixu") ?: root.createDirectory(".zhixu") ?: return null
        return zhixu.findFile("plugins") ?: zhixu.createDirectory("plugins")
    }

    private fun ensurePluginConfigFile(rootUri: Uri, pluginId: String, fileName: String): DocumentFile? {
        val pluginsDir = ensurePluginsDir(rootUri) ?: return null
        val pluginDir = pluginsDir.findFile(pluginId) ?: return null
        return findFileLoose(pluginDir, fileName) ?: createFilePreservingName(pluginDir, mimeTypeForName(fileName), fileName)
    }

    private fun ensurePluginStateFile(pluginsDir: DocumentFile): DocumentFile? {
        val state = findFileLoose(pluginsDir, "state.json")
            ?: createFilePreservingName(pluginsDir, mimeTypeForName("state.json"), "state.json")
            ?: return null
        if (state.length() == 0L) {
            writeJsonObject(state.uri, JSONObject().put("enabled", JSONArray()).put("sources", JSONObject()))
        }
        return state
    }

    private fun createFilePreservingName(parent: DocumentFile, mimeType: String, displayName: String): DocumentFile? {
        if (parent.uri.scheme.equals("file", ignoreCase = true)) {
            val dir = parent.uri.path?.let(::File) ?: return null
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, displayName)
            runCatching { if (!file.exists()) file.createNewFile() }
            return DocumentFile.fromFile(file)
        }
        val created =
            parent.createFile(mimeType, displayName)
                ?: parent.createFile("application/octet-stream", displayName)
                ?: return null
        if (!created.name.equals(displayName, ignoreCase = false)) {
            val already = parent.findFile(displayName)
            if (already == null) runCatching { created.renameTo(displayName) }
        }
        return created
    }

    private fun readEnabledSet(pluginsDir: DocumentFile): Set<String> {
        val stateFile = ensurePluginStateFile(pluginsDir) ?: return emptySet()
        val obj = readJsonObject(stateFile.uri) ?: return emptySet()
        val enabled = obj.optJSONArray("enabled") ?: return emptySet()
        return enabled.toStringList().toSet()
    }

    private fun readPluginSource(pluginsDir: DocumentFile, pluginId: String): PluginSource? {
        val stateFile = ensurePluginStateFile(pluginsDir) ?: return null
        val obj = readJsonObject(stateFile.uri) ?: return null
        val sources = obj.optJSONObject("sources") ?: return null
        val s = sources.optJSONObject(pluginId) ?: return null
        val type = s.optString("type").trim()
        if (type.isBlank()) return null
        val url = s.optString("url").trim().takeIf { it.isNotBlank() }
        return PluginSource(type = type, url = url)
    }

    private fun clearPluginSource(pluginsDir: DocumentFile, pluginId: String) {
        val stateFile = ensurePluginStateFile(pluginsDir) ?: return
        val obj = readJsonObject(stateFile.uri) ?: JSONObject()
        val sources = obj.optJSONObject("sources") ?: JSONObject()
        sources.remove(pluginId)
        obj.put("sources", sources)
        writeJsonObject(stateFile.uri, obj)
    }

    private fun recordPluginSource(rootUri: Uri, pluginId: String, source: PluginSource) {
        val pluginsDir = ensurePluginsDir(rootUri) ?: return
        val stateFile = ensurePluginStateFile(pluginsDir) ?: return
        val obj = readJsonObject(stateFile.uri) ?: JSONObject()
        val sources = obj.optJSONObject("sources") ?: JSONObject()
        val s = JSONObject().put("type", source.type)
        if (!source.url.isNullOrBlank()) s.put("url", source.url)
        sources.put(pluginId, s)
        obj.put("sources", sources)
        writeJsonObject(stateFile.uri, obj)
    }

    private fun readManifest(pluginDir: DocumentFile): PluginManifest? {
        val manifestFile =
            findFileLoose(pluginDir, "manifest.json")
                ?.takeIf { it.isFile }
                ?: return null
        val text = readText(manifestFile.uri) ?: return null
        return parseManifestFromText(text)
    }

    private fun parseManifestFromText(text: String): PluginManifest? {
        val json = runCatching { JSONObject(text) }.getOrNull() ?: return null
        val id = json.optString("id").trim()
        if (id.isBlank()) return null
        val actions = parseActions(json.optJSONArray("actions"))
        val files =
            json.optJSONArray("files")?.let { arr ->
                (0 until arr.length()).mapNotNull { idx -> arr.optString(idx).trim().takeIf { it.isNotBlank() } }
            } ?: emptyList()
        return PluginManifest(
            id = id,
            name = json.optString("name").takeIf { it.isNotBlank() },
            version = json.optString("version").takeIf { it.isNotBlank() },
            description = json.optString("description").takeIf { it.isNotBlank() },
            entry = json.optString("entry").takeIf { it.isNotBlank() },
            files = files,
            actions = actions,
        )
    }

    private fun parseActions(arr: JSONArray?): List<PluginActionSpec> {
        if (arr == null || arr.length() == 0) return emptyList()
        val out = ArrayList<PluginActionSpec>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val actionId = obj.optString("id").trim()
            val label = obj.optString("label").trim()
            if (actionId.isBlank() || label.isBlank()) continue
            out += PluginActionSpec(
                id = actionId,
                label = label,
                icon = obj.optString("icon").takeIf { it.isNotBlank() },
                place = obj.optString("place").takeIf { it.isNotBlank() },
                ringIndex = obj.optInt("ringIndex").takeIf { obj.has("ringIndex") },
            )
        }
        return out
    }

    private fun resolveEntryFileNames(manifest: PluginManifest): List<String> {
        val entry = manifest.entry?.trim().orEmpty()
        if (entry.isBlank()) return listOf("main.js")
        return if (entry.endsWith(".js", ignoreCase = true)) listOf(entry) else listOf(entry, "$entry.js")
    }

    private fun deepMergeDefaults(defaults: JSONObject, current: JSONObject): JSONObject {
        val out = JSONObject(defaults.toString())
        val keys = current.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val curVal = current.opt(k)
            val defVal = out.opt(k)
            if (curVal is JSONObject && defVal is JSONObject) {
                out.put(k, deepMergeDefaults(defVal, curVal))
            } else {
                out.put(k, curVal)
            }
        }
        return out
    }

    private fun syncFromOfficialIntoExisting(
        pluginId: String,
        destDir: DocumentFile,
        writeMergedConfig: (JSONObject?) -> Unit,
    ): InstallResult {
        val base = "$officialBaseUrl/plugins/${pluginId.trim()}"
        val manifestText = httpGetText("$base/manifest.json") ?: return InstallResult(false, "Failed to download manifest.json", pluginId)
        val manifest = parseManifestFromText(manifestText) ?: return InstallResult(false, "Invalid manifest.json", pluginId)

        val required = LinkedHashSet<String>()
        required += "manifest.json"
        required += resolveEntryFileNames(manifest)
        required += manifest.files

        var defaultsConfig: JSONObject? = null
        for (name in required) {
            if (name.equals("config.json", ignoreCase = true)) {
                val bytes = httpGetBytes("$base/$name") ?: continue
                defaultsConfig = runCatching { JSONObject(bytes.toString(Charsets.UTF_8)) }.getOrNull()
                continue
            }
            val bytes = httpGetBytes("$base/$name") ?: return InstallResult(false, "Failed to download: $name", pluginId)
            val outFile =
                findFileLoose(destDir, name)
                    ?: createFilePreservingName(destDir, mimeTypeForName(name), name)
                    ?: return InstallResult(false, "Failed to create: $name", pluginId)
            if (!writeBytes(outFile.uri, bytes)) return InstallResult(false, "Failed to write: $name", pluginId)
        }

        writeMergedConfig(defaultsConfig)

        val readmeNames = listOf("README.md", "readme.md")
        for (n in readmeNames) {
            val bytes = httpGetBytes("$base/$n") ?: continue
            val outFile = findFileLoose(destDir, n) ?: createFilePreservingName(destDir, mimeTypeForName(n), n) ?: continue
            writeBytes(outFile.uri, bytes)
            break
        }

        return InstallResult(true, "Updated: ${manifest.name ?: manifest.id}", manifest.id)
    }

    private fun syncFromGitIntoExisting(
        pluginId: String,
        gitUrl: String,
        destDir: DocumentFile,
        writeMergedConfig: (JSONObject?) -> Unit,
    ): InstallResult {
        val tmp = File(context.cacheDir, "plugins-update-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            runCatching {
                Git.cloneRepository()
                    .setURI(gitUrl.trim())
                    .setDirectory(tmp)
                    .call()
            }.getOrElse { e ->
                return InstallResult(false, "Git clone failed: ${e.message ?: e.javaClass.simpleName}", pluginId)
            }

            val manifestFile = File(tmp, "manifest.json")
            if (!manifestFile.exists()) return InstallResult(false, "manifest.json not found at repo root", pluginId)
            val manifestText =
                runCatching { manifestFile.readText(Charsets.UTF_8) }
                    .getOrElse { return InstallResult(false, "Failed to read manifest.json", pluginId) }
            val manifest =
                parseManifestFromText(manifestText)
                    ?: return InstallResult(false, "Invalid manifest.json", pluginId)
            if (manifest.id != pluginId) return InstallResult(false, "manifest.id='${manifest.id}' does not match pluginId='$pluginId'", pluginId)

            val defaultsConfig = File(tmp, "config.json").takeIf { it.isFile }?.let { f -> runCatching { JSONObject(f.readText(Charsets.UTF_8)) }.getOrNull() }

            val ok =
                copyFileDirRecursively(tmp, destDir) { file ->
                    val name = file.name
                    name != ".git" && name != ".github" && !name.equals("config.json", ignoreCase = true)
                }
            if (!ok) return InstallResult(false, "Failed to copy plugin files", pluginId)

            writeMergedConfig(defaultsConfig)

            return InstallResult(true, "Updated: ${manifest.name ?: manifest.id}", manifest.id)
        } finally {
            tmp.deleteRecursively()
        }
    }

    private fun httpGetText(url: String): String? =
        httpGetBytes(url)?.toString(Charsets.UTF_8)

    private fun httpGetBytes(url: String): ByteArray? {
        val request = Request.Builder().url(url).get().build()
        return runCatching {
            http.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                resp.body?.bytes()
            }
        }.getOrNull()
    }

    private fun readText(uri: Uri): String? {
        val resolver: ContentResolver = context.contentResolver
        return runCatching {
            resolver.openInputStream(uri)?.use { input -> input.readBytes().toString(Charsets.UTF_8) }
        }.getOrNull()
    }

    private fun readJsonObject(uri: Uri): JSONObject? =
        runCatching {
            val text = readText(uri) ?: return@runCatching null
            JSONObject(text)
        }.getOrNull()

    private fun writeJsonObject(uri: Uri, json: JSONObject) {
        val resolver: ContentResolver = context.contentResolver
        runCatching {
            resolver.openOutputStream(uri, "wt")?.use { out ->
                out.write((json.toString(2) + "\n").toByteArray(Charsets.UTF_8))
            }
        }.onFailure { e ->
            Log.e("Zhixu", "writeJsonObject failed: $uri", e)
        }
    }

    private fun copyDocumentDirRecursively(srcDir: DocumentFile, destDir: DocumentFile): Boolean {
        srcDir.listFiles().forEach { src ->
            val name = src.name ?: return@forEach
            if (src.isDirectory) {
                val childDest = destDir.findFile(name) ?: destDir.createDirectory(name) ?: return false
                if (!copyDocumentDirRecursively(src, childDest)) return false
            } else if (src.isFile) {
                val bytes = readBytes(src.uri) ?: return false
                val destFile = findFileLoose(destDir, name) ?: createFilePreservingName(destDir, mimeTypeForName(name), name) ?: return false
                if (!writeBytes(destFile.uri, bytes)) return false
            }
        }
        return true
    }

    private fun readBytes(uri: Uri): ByteArray? {
        val resolver: ContentResolver = context.contentResolver
        return runCatching { resolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
    }

    private fun writeBytes(uri: Uri, bytes: ByteArray): Boolean {
        val resolver: ContentResolver = context.contentResolver
        return runCatching {
            resolver.openOutputStream(uri, "wt")?.use { out -> out.write(bytes) } ?: return@runCatching false
            true
        }.getOrDefault(false)
    }

    private fun copyFileDirRecursively(src: File, destDir: DocumentFile, accept: (File) -> Boolean): Boolean {
        val children = src.listFiles() ?: return true
        for (child in children) {
            if (!accept(child)) continue
            if (child.isDirectory) {
                val sub = destDir.findFile(child.name) ?: destDir.createDirectory(child.name) ?: return false
                if (!copyFileDirRecursively(child, sub, accept)) return false
            } else if (child.isFile) {
                val bytes = runCatching { child.readBytes() }.getOrNull() ?: return false
                val destFile = findFileLoose(destDir, child.name) ?: createFilePreservingName(destDir, mimeTypeForName(child.name), child.name) ?: return false
                if (!writeBytes(destFile.uri, bytes)) return false
            }
        }
        return true
    }

    private fun findFileLoose(parent: DocumentFile, desiredName: String): DocumentFile? {
        parent.findFile(desiredName)?.let { return it }
        val desiredNorm = normalizeFileName(desiredName).lowercase()
        val candidate =
            parent.listFiles()
                .firstOrNull { it.isFile && normalizeFileName(it.name ?: "").lowercase() == desiredNorm }
                ?: return null

        if (!candidate.name.equals(desiredName, ignoreCase = false) && parent.findFile(desiredName) == null) {
            runCatching { candidate.renameTo(desiredName) }
        }
        return parent.findFile(desiredName) ?: candidate
    }

    private fun normalizeFileName(name: String): String {
        var out = name
        while (out.endsWith(".bin", ignoreCase = true)) {
            out = out.dropLast(".bin".length)
        }
        while (true) {
            val m = DUPLICATE_EXT_REGEX.matchEntire(out) ?: break
            out = "${m.groupValues[1]}.${m.groupValues[2]}"
        }
        return out
    }

    private fun mimeTypeForName(name: String): String {
        val ext = name.substringAfterLast('.', missingDelimiterValue = "").lowercase()
        return when (ext) {
            "json" -> "application/json"
            "js" -> "application/javascript"
            "mjs" -> "application/javascript"
            "md" -> "text/markdown"
            "txt" -> "text/plain"
            "html" -> "text/html"
            "css" -> "text/css"
            "svg" -> "image/svg+xml"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            "xml" -> "application/xml"
            else -> "application/octet-stream"
        }
    }

    private companion object {
        val DUPLICATE_EXT_REGEX = Regex("""^(.*)\.([A-Za-z0-9]{1,8})\.\2$""", RegexOption.IGNORE_CASE)
    }
}

private fun JSONArray.toStringList(): List<String> =
    (0 until length())
        .mapNotNull { idx -> optString(idx).takeIf { it.isNotBlank() } }
